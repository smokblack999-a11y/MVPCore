package telegram.core.tdlib;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import telegram.core.api.AuthorizationState;
import telegram.core.api.Chat;
import telegram.core.api.MediaSpec;
import telegram.core.api.Message;
import telegram.core.api.Page;
import telegram.core.api.SendOptions;
import telegram.core.api.TelegramError;
import telegram.core.internal.TelegramTransport;
import telegram.core.api.TransferProgress;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TDLib JSON transport for a normal Telegram user account.
 * Generated TDLib classes never cross the public API boundary.
 */
public final class TdLibTransport implements TelegramTransport, AutoCloseable {
    private static final Gson GSON = new Gson();
    private final TdLibTransportConfig config;
    private final TdJsonBridge td;
    private final int clientId;
    private final AtomicLong requestIds = new AtomicLong(1L);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> requestTimeouts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Chat> chatCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> transferNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> transferPaths = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            2, new NamedDaemonFactory("tdlib-"));
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    private final Thread receiver;
    private volatile TelegramTransport.Listener listener;
    private volatile AuthorizationState state = new AuthorizationState(AuthorizationState.Type.UNKNOWN);
    private volatile boolean mainChatListExhausted;

    public TdLibTransport(TdLibTransportConfig config) {
        if (config == null) throw new IllegalArgumentException("config required");
        this.config = config;
        new File(config.databaseDirectory).mkdirs();
        new File(config.filesDirectory).mkdirs();
        this.td = TdJsonBridge.load();
        this.clientId = td.createClientId();
        this.receiver = new Thread(this::receiveLoop, "tdlib-transport-receiver");
        this.receiver.setDaemon(true);
        this.receiver.start();
        try {
            td.send(clientId, "{\"@type\":\"getAuthorizationState\"}");
        } catch (RuntimeException error) {
            shutdownOnFailure(error);
            throw error;
        }
    }

    @Override public void setListener(TelegramTransport.Listener listener) {
        this.listener = listener;
    }

    @Override public CompletableFuture<AuthorizationState> authorizationState() {
        return CompletableFuture.completedFuture(state);
    }

    @Override public CompletableFuture<Void> requestQrCodeAuthentication() {
        JsonObject args = new JsonObject();
        args.add("other_user_ids", new JsonArray());
        return request("requestQrCodeAuthentication", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setPhoneNumber(String phoneNumber) {
        // Keep the request minimal and let the pinned TDLib runtime apply its
        // own default authentication settings. This avoids coupling the host
        // to optional settings that may evolve between TDLib releases.
        JsonObject args = new JsonObject();
        args.addProperty("phone_number", required(phoneNumber, "phoneNumber"));
        return request("setAuthenticationPhoneNumber", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setEmailAddress(String emailAddress) {
        JsonObject args = new JsonObject();
        args.addProperty("email_address", required(emailAddress, "emailAddress"));
        return request("setAuthenticationEmailAddress", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setEmailCode(String code) {
        JsonObject auth = new JsonObject();
        auth.addProperty("@type", "emailAddressAuthenticationCode");
        auth.addProperty("code", required(code, "emailCode"));
        JsonObject args = new JsonObject();
        args.add("code", auth);
        return request("checkAuthenticationEmailCode", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setCode(String code) {
        JsonObject args = new JsonObject();
        args.addProperty("code", required(code, "code"));
        return request("checkAuthenticationCode", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> resendCode() {
        JsonObject reason = new JsonObject();
        reason.addProperty("@type", "resendCodeReasonUserRequest");
        JsonObject args = new JsonObject();
        args.add("reason", reason);
        return request("resendAuthenticationCode", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setPassword(String password) {
        JsonObject args = new JsonObject();
        args.addProperty("password", required(password, "password"));
        return request("checkAuthenticationPassword", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> register(String firstName, String lastName) {
        JsonObject args = new JsonObject();
        args.addProperty("first_name", required(firstName, "firstName"));
        args.addProperty("last_name", lastName == null ? "" : lastName);
        args.addProperty("disable_notification", false);
        return request("registerUser", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> logout() {
        return request("logOut", new JsonObject()).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> close() {
        if (state.type == AuthorizationState.Type.CLOSED) return CompletableFuture.completedFuture(null);
        if (!closing.compareAndSet(false, true)) return closeFuture;

        try {
            td.send(clientId, "{\"@type\":\"close\"}");
        } catch (RuntimeException error) {
            shutdownOnFailure(error);
            closeFuture.completeExceptionally(error);
            return closeFuture;
        }

        scheduler.schedule(() -> {
            if (!closeFuture.isDone()) {
                IllegalStateException error = new IllegalStateException("TDLib close timed out");
                shutdownOnFailure(error);
                closeFuture.completeExceptionally(error);
            }
        }, config.closeTimeoutMs, TimeUnit.MILLISECONDS);

        return closeFuture;
    }

    @Override public CompletableFuture<Page<Chat>> chats(int limit, long cursor) {
        final int safeLimit = clamp(limit);
        final long safeCursor = Math.max(0L, cursor);
        final long neededLong = Math.min(10_000L, safeCursor + safeLimit + 1L);
        final int needed = (int) neededLong;

        return ensureMainChatsLoaded(needed, 100)
                .thenCompose(v -> {
                    List<Long> ids = orderedMainChatIds();
                    int start = (int) Math.min((long) ids.size(), safeCursor);
                    int end = Math.min(ids.size(), start + safeLimit);
                    List<CompletableFuture<Chat>> futures = new ArrayList<>();
                    for (int i = start; i < end; i++) {
                        long chatId = ids.get(i);
                        Chat cached = chatCache.get(chatId);
                        futures.add(cached != null
                                ? CompletableFuture.completedFuture(cached)
                                : getChat(chatId));
                    }
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> {
                                List<Chat> result = new ArrayList<>(futures.size());
                                for (CompletableFuture<Chat> future : futures) result.add(future.join());
                                long next = safeCursor + result.size();
                                boolean hasMore = end < ids.size() || !mainChatListExhausted;
                                return new Page<>(result, next, hasMore);
                            });
                });
    }

    private CompletableFuture<Void> ensureMainChatsLoaded(final int needed, final int attemptsLeft) {
        if (orderedMainChatIds().size() >= needed || mainChatListExhausted || attemptsLeft <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        JsonObject list = new JsonObject();
        list.addProperty("@type", "chatListMain");
        int current = orderedMainChatIds().size();
        int requestLimit = Math.max(1, Math.min(100, needed - current));
        JsonObject args = new JsonObject();
        args.add("chat_list", list);
        args.addProperty("limit", requestLimit);
        return request("loadChats", args)
                .handle((ignored, error) -> {
                    if (error == null) return null;
                    if (isAllChatsLoaded(error)) {
                        mainChatListExhausted = true;
                        return null;
                    }
                    throw propagate(error);
                })
                .thenCompose(ignored -> ensureMainChatsLoaded(needed, attemptsLeft - 1));
    }

    private List<Long> orderedMainChatIds() {
        List<Map.Entry<Long, Chat>> chats = new ArrayList<>(chatCache.entrySet());
        chats.removeIf(entry -> mainOrder(entry.getKey()) == null);
        chats.sort((a, b) -> {
            long ao = mainOrder(a.getKey());
            long bo = mainOrder(b.getKey());
            int order = Long.compare(bo, ao);
            return order != 0 ? order : Long.compare(b.getKey(), a.getKey());
        });
        List<Long> result = new ArrayList<>(chats.size());
        for (Map.Entry<Long, Chat> entry : chats) result.add(entry.getKey());
        return result;
    }

    private Long mainOrder(long chatId) {
        Chat chat = chatCache.get(chatId);
        return chat == null ? null : chatOrderById.get(chatId);
    }

    private final ConcurrentHashMap<Long, Long> chatOrderById = new ConcurrentHashMap<>();

    private CompletableFuture<Chat> getChat(long chatId) {
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        return request("getChat", args).thenApply(json -> {
            Chat chat = decodeChat(json);
            cacheChat(chat);
            cacheMainPositions(json, chat.id);
            return chat;
        });
    }

    @Override public CompletableFuture<Page<Message>> messages(long chatId, int limit, long fromMessageId) {
        if (chatId == 0L) return failed(new IllegalArgumentException("chatId required"));
        int safeLimit = clamp(limit);
        long cursor = Math.max(0L, fromMessageId);

        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.addProperty("from_message_id", cursor);
        args.addProperty("offset", 0);
        // TDLib includes from_message_id when cursor != 0. Ask for one extra
        // item so a limit=1 page can still advance without dropping history.
        int requestLimit = cursor == 0L ? safeLimit : Math.min(100, safeLimit + 1);
        args.addProperty("limit", requestLimit);
        args.addProperty("only_local", false);

        return request("getChatHistory", args).thenApply(response -> {
            int responseCount = response.has("messages") && response.get("messages").isJsonArray()
                    ? response.getAsJsonArray("messages").size() : 0;
            List<Message> raw = new ArrayList<>(MessageJson.decodeList(response));
            if (cursor != 0L) {
                raw.removeIf(message -> message.id == cursor);
            }
            long next = raw.isEmpty() ? cursor : raw.get(raw.size() - 1).id;
            boolean hasMore = responseCount >= requestLimit && !raw.isEmpty();
            return new Page<>(raw, next, hasMore);
        });
    }

    @Override public CompletableFuture<Message> sendText(long chatId, String text, SendOptions options) {
        if (chatId == 0L) return failed(new IllegalArgumentException("chatId required"));
        JsonObject content = new JsonObject();
        content.addProperty("@type", "inputMessageText");
        content.add("text", formatted(required(text, "text")));
        content.add("link_preview_options", JsonNull.INSTANCE);
        content.addProperty("clear_draft", false);
        JsonObject args = sendBase(chatId, options);
        args.add("input_message_content", content);
        return request("sendMessage", args).thenApply(MessageJson::decode);
    }

    @Override public CompletableFuture<Message> sendMedia(long chatId, MediaSpec spec, SendOptions options) {
        if (chatId == 0L) return failed(new IllegalArgumentException("chatId required"));
        if (spec == null || spec.path == null || spec.path.trim().isEmpty()) {
            return failed(new IllegalArgumentException("media path required"));
        }

        File file = new File(spec.path);
        if (!file.isFile() || !file.canRead()) {
            return failed(new IllegalArgumentException("media file not readable"));
        }

        String mime = mediaMime(spec, file).toLowerCase(Locale.ROOT);
        JsonObject args = sendBase(chatId, options);
        JsonObject content;

        if (mime.startsWith("image/")) {
            content = new JsonObject();
            content.addProperty("@type", "inputMessagePhoto");
            content.add("photo", inputFile(file));
            content.add("thumbnail", JsonNull.INSTANCE);
            content.add("added_sticker_file_ids", new JsonArray());
            content.addProperty("width", 0);
            content.addProperty("height", 0);
            content.add("caption", formatted(options == null ? "" : options.caption));
            content.addProperty("show_caption_above_media", false);
            content.add("self_destruct_type", JsonNull.INSTANCE);
            content.addProperty("has_spoiler", false);
        } else if (mime.startsWith("video/")) {
            content = new JsonObject();
            content.addProperty("@type", "inputMessageVideo");
            content.add("video", inputFile(file));
            content.add("thumbnail", JsonNull.INSTANCE);
            content.add("cover", JsonNull.INSTANCE);
            content.addProperty("start_timestamp", 0);
            content.add("added_sticker_file_ids", new JsonArray());
            content.addProperty("duration", 0);
            content.addProperty("width", 0);
            content.addProperty("height", 0);
            content.addProperty("supports_streaming", true);
            content.add("caption", formatted(options == null ? "" : options.caption));
            content.addProperty("show_caption_above_media", false);
            content.add("self_destruct_type", JsonNull.INSTANCE);
            content.addProperty("has_spoiler", false);
        } else {
            content = new JsonObject();
            content.addProperty("@type", "inputMessageDocument");
            content.add("document", inputFile(file));
            content.add("thumbnail", JsonNull.INSTANCE);
            content.add("caption", formatted(options == null ? "" : options.caption));
        }

        args.add("input_message_content", content);
        transferPaths.put(file.getAbsolutePath(),
                spec.fileName == null || spec.fileName.trim().isEmpty() ? file.getName() : spec.fileName);
        return request("sendMessage", args).thenApply(MessageJson::decode);
    }

    private JsonObject sendBase(long chatId, SendOptions options) {
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.add("topic_id", JsonNull.INSTANCE);
        args.add("reply_to", JsonNull.INSTANCE);
        if (options == null) {
            args.add("options", JsonNull.INSTANCE);
        } else {
            JsonObject sendOptions = new JsonObject();
            sendOptions.addProperty("@type", "messageSendOptions");
            sendOptions.add("suggested_post_info", JsonNull.INSTANCE);
            sendOptions.addProperty("disable_notification", options.disableNotification);
            sendOptions.addProperty("from_background", false);
            sendOptions.addProperty("protect_content", false);
            sendOptions.addProperty("allow_paid_broadcast", false);
            sendOptions.addProperty("paid_message_star_count", 0);
            sendOptions.addProperty("update_order_of_installed_sticker_sets", false);
            sendOptions.add("scheduling_state", JsonNull.INSTANCE);
            sendOptions.addProperty("effect_id", 0L);
            sendOptions.addProperty("sending_id", 0);
            sendOptions.addProperty("only_preview", false);
            args.add("options", sendOptions);
        }
        args.add("reply_markup", JsonNull.INSTANCE);
        return args;
    }

    private JsonObject inputFile(File file) {
        JsonObject input = new JsonObject();
        input.addProperty("@type", "inputFileLocal");
        input.addProperty("path", file.getAbsolutePath());
        return input;
    }

    private static String mediaMime(MediaSpec spec, File file) {
        if (spec.mimeType != null && !spec.mimeType.trim().isEmpty()) {
            return spec.mimeType.trim();
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext)
                || "webp".equals(ext) || "heic".equals(ext) || "heif".equals(ext)) {
            return "image/" + ("jpg".equals(ext) || "jpeg".equals(ext) ? "jpeg" : ext);
        }
        if ("mp4".equals(ext) || "m4v".equals(ext) || "mov".equals(ext)
                || "webm".equals(ext) || "mkv".equals(ext) || "3gp".equals(ext)) {
            return "video/" + ("3gp".equals(ext) ? "3gpp" : ext);
        }
        return "application/octet-stream";
    }


    private CompletableFuture<JsonObject> request(String type, JsonObject args) {
        if (!running.get() || closing.get()) {
            return failed(new IllegalStateException("TDLib transport is closing or closed"));
        }

        long id = requestIds.getAndIncrement();
        JsonObject request = args == null ? new JsonObject() : args.deepCopy();
        request.addProperty("@type", type);
        request.addProperty("@extra", Long.toString(id));

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, future);

        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            if (pending.remove(id, future)) {
                TelegramRuntimeError error = new TelegramRuntimeError(
                        new TelegramError(TelegramError.Code.NETWORK, "TDLib request timed out"));
                future.completeExceptionally(error);
            }
        }, config.requestTimeoutMs, TimeUnit.MILLISECONDS);
        requestTimeouts.put(id, timeout);

        future.whenComplete((value, error) -> {
            pending.remove(id, future);
            ScheduledFuture<?> task = requestTimeouts.remove(id);
            if (task != null) task.cancel(false);
        });

        try {
            td.send(clientId, GSON.toJson(request));
        } catch (RuntimeException error) {
            pending.remove(id, future);
            ScheduledFuture<?> task = requestTimeouts.remove(id);
            if (task != null) task.cancel(false);
            future.completeExceptionally(error);
        }
        return future;
    }

    private void receiveLoop() {
        while (running.get()) {
            try {
                String raw = td.receive(1.0);
                if (raw == null || raw.isEmpty()) continue;
                JsonObject update = JsonParser.parseString(raw).getAsJsonObject();
                handle(update);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable error) {
                if (running.get()) notifyError(TelegramJsonErrorMapper.fromThrowable(error));
            }
        }
    }

    private void handle(JsonObject update) {
        if (update.has("@extra")) {
            try {
                long id = Long.parseLong(update.get("@extra").getAsString());
                CompletableFuture<JsonObject> future = pending.remove(id);
                if (future != null) {
                    ScheduledFuture<?> task = requestTimeouts.remove(id);
                    if (task != null) task.cancel(false);
                    if ("error".equals(type(update))) {
                        future.completeExceptionally(new TelegramRuntimeError(
                                TelegramJsonErrorMapper.fromJson(update)));
                    } else {
                        future.complete(update);
                    }
                    return;
                }
            } catch (RuntimeException ignored) {
                // An external/unsolicited update may carry a non-numeric @extra.
            }
        }

        String type = type(update);
        if (type.startsWith("authorizationState")) {
            // getAuthorizationState returns the state directly; normal state changes arrive as updateAuthorizationState.
            handleAuth(update);
            return;
        }
        switch (type) {
            case "updateAuthorizationState":
                handleAuth(update.getAsJsonObject("authorization_state"));
                break;
            case "updateNewChat":
                handleNewChat(update.getAsJsonObject("chat"));
                break;
            case "updateChatTitle":
                handleChatTitle(update);
                break;
            case "updateChatLastMessage":
                handleChatLastMessage(update);
                break;
            case "updateChatPosition":
                handleChatPosition(update);
                break;
            case "updateChatAddedToList":
                handleChatAddedToList(update);
                break;
            case "updateChatRemovedFromList":
                handleChatRemovedFromList(update);
                break;
            case "updateChatDraftMessage":
                cachePositions(update.getAsJsonArray("positions"), update.get("chat_id").getAsLong());
                break;
            case "updateNewMessage":
                TelegramTransport.Listener l = listener;
                if (l != null && update.has("message") && update.get("message").isJsonObject()) {
                    l.onMessage(MessageJson.decode(update.getAsJsonObject("message")));
                }
                break;
            case "updateMessageSendSucceeded":
                handleMessageSendSucceeded(update);
                break;
            case "updateMessageSendFailed":
                handleMessageSendFailed(update);
                break;
            case "updateMessageSendAcknowledged":
                // Intermediate acknowledgement; the final message is emitted
                // by updateMessageSendSucceeded.
                break;
            case "updateFile":
                handleFile(update.getAsJsonObject("file"));
                break;
            default:
                break;
        }
    }

    private void handleAuth(JsonObject auth) {
        if (auth == null || !auth.has("@type")) return;
        String authType = auth.get("@type").getAsString();

        if ("authorizationStateWaitTdlibParameters".equals(authType)) {
            JsonObject p = new JsonObject();
            p.addProperty("use_test_dc", false);
            p.addProperty("database_directory", config.databaseDirectory);
            p.addProperty("files_directory", config.filesDirectory);
            p.addProperty("database_encryption_key", StandardBase64.encode(config.databaseEncryptionKey));
            p.addProperty("use_file_database", true);
            p.addProperty("use_chat_info_database", true);
            p.addProperty("use_message_database", true);
            p.addProperty("use_secret_chats", true);
            p.addProperty("api_id", config.apiId);
            p.addProperty("api_hash", config.apiHash);
            p.addProperty("system_language_code", config.systemLanguageCode);
            p.addProperty("device_model", config.deviceModel);
            p.addProperty("system_version", config.systemVersion);
            p.addProperty("application_version", config.applicationVersion);
            notifyRequestFailure(request("setTdlibParameters", p));
        } else if ("authorizationStateWaitEncryptionKey".equals(authType)) {
            JsonObject p = new JsonObject();
            p.addProperty("encryption_key", StandardBase64.encode(config.databaseEncryptionKey));
            notifyRequestFailure(request("checkDatabaseEncryptionKey", p));
        }

        AuthorizationState mapped = AuthorizationStateMapper.map(auth);
        state = mapped;
        if (mapped.type == AuthorizationState.Type.CLOSED) {
            finishClosed();
        }

        TelegramTransport.Listener l = listener;
        if (l != null) l.onAuthorizationState(mapped);
    }

    private void handleNewChat(JsonObject chatJson) {
        if (chatJson == null) return;
        Chat chat = decodeChat(chatJson);
        cacheChat(chat);
        cacheMainPositions(chatJson, chat.id);
    }

    private void handleChatTitle(JsonObject update) {
        long chatId = update.get("chat_id").getAsLong();
        Chat old = chatCache.get(chatId);
        String title = update.has("title") ? update.get("title").getAsString() : "";
        cacheChat(new Chat(chatId, title, old == null ? 0L : old.lastMessageId));
    }

    private void handleChatLastMessage(JsonObject update) {
        long chatId = update.get("chat_id").getAsLong();
        Chat old = chatCache.get(chatId);
        long lastId = 0L;
        if (update.has("last_message") && update.get("last_message").isJsonObject()) {
            JsonObject last = update.getAsJsonObject("last_message");
            if (last.has("id")) lastId = last.get("id").getAsLong();
        }
        cacheChat(new Chat(chatId, old == null ? "" : old.title, lastId));
        cachePositions(update.getAsJsonArray("positions"), chatId);
    }

    private void handleChatPosition(JsonObject update) {
        long chatId = update.get("chat_id").getAsLong();
        JsonObject position = update.has("position") && update.get("position").isJsonObject()
                ? update.getAsJsonObject("position") : null;
        if (position == null || !isMainList(position.get("list"))) return;
        long order = parseInt64(position.get("order"));
        if (order == 0L) {
            chatOrderById.remove(chatId);
            return;
        }
        chatOrderById.put(chatId, order);
    }

    private void handleChatAddedToList(JsonObject update) {
        long chatId = update.get("chat_id").getAsLong();
        JsonObject position = update.has("position") && update.get("position").isJsonObject()
                ? update.getAsJsonObject("position") : null;
        if (position == null || !isMainList(position.get("list"))) return;
        long order = parseInt64(position.get("order"));
        if (order == 0L) chatOrderById.remove(chatId);
        else chatOrderById.put(chatId, order);
    }

    private void handleChatRemovedFromList(JsonObject update) {
        long chatId = update.get("chat_id").getAsLong();
        JsonObject list = update.has("chat_list") && update.get("chat_list").isJsonObject()
                ? update.getAsJsonObject("chat_list") : null;
        if (isMainList(list)) {
            chatOrderById.remove(chatId);
        }
    }

    private void cacheMainPositions(JsonObject chatJson, long chatId) {
        if (chatJson == null) return;
        if (chatJson.has("positions") && chatJson.get("positions").isJsonArray()) {
            cachePositions(chatJson.getAsJsonArray("positions"), chatId);
        }
    }

    private void cachePositions(JsonArray positions, long chatId) {
        if (positions == null) return;
        for (int i = 0; i < positions.size(); i++) {
            if (!positions.get(i).isJsonObject()) continue;
            JsonObject position = positions.get(i).getAsJsonObject();
            if (!isMainList(position.get("list"))) continue;
            long order = parseInt64(position.get("order"));
            if (order == 0L) chatOrderById.remove(chatId);
            else chatOrderById.put(chatId, order);
        }
    }

    private boolean isMainList(com.google.gson.JsonElement list) {
        return list != null && list.isJsonObject()
                && "chatListMain".equals(type(list.getAsJsonObject()));
    }

    private static long parseInt64(com.google.gson.JsonElement value) {
        try {
            return value == null ? 0L : Long.parseLong(value.getAsString());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private Chat decodeChat(JsonObject json) {
        long chatId = json.has("id") ? json.get("id").getAsLong() : 0L;
        String title = json.has("title") ? json.get("title").getAsString() : "";
        long lastMessageId = 0L;
        if (json.has("last_message") && json.get("last_message").isJsonObject()) {
            JsonObject last = json.getAsJsonObject("last_message");
            if (last.has("id")) lastMessageId = last.get("id").getAsLong();
        }
        return new Chat(chatId, title, lastMessageId);
    }

    private void cacheChat(Chat chat) {
        if (chat != null && chat.id != 0L) chatCache.put(chat.id, chat);
    }

    private void handleMessageSendSucceeded(JsonObject update) {
        TelegramTransport.Listener l = listener;
        if (l != null && update.has("message") && update.get("message").isJsonObject()) {
            l.onMessage(MessageJson.decode(update.getAsJsonObject("message")));
        }
    }

    private void handleMessageSendFailed(JsonObject update) {
        if (update == null) return;
        int code = safeInt(update, "error_code");
        String message = update.has("error_message")
                ? update.get("error_message").getAsString()
                : "Telegram message send failed";
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        notifyError(TelegramJsonErrorMapper.fromJson(error));
    }

    private static int safeInt(JsonObject object, String name) {
        try { return object.has(name) ? object.get(name).getAsInt() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }

    private void handleFile(JsonObject file) {
        if (file == null) return;
        int fileId = file.has("id") ? file.get("id").getAsInt() : 0;
        JsonObject local = file.has("local") && file.get("local").isJsonObject()
                ? file.getAsJsonObject("local") : null;
        JsonObject remote = file.has("remote") && file.get("remote").isJsonObject()
                ? file.getAsJsonObject("remote") : null;

        long downloaded = local != null && local.has("downloaded_size")
                ? local.get("downloaded_size").getAsLong() : 0L;
        long uploaded = remote != null && remote.has("uploaded_size")
                ? remote.get("uploaded_size").getAsLong() : 0L;

        String localPath = local != null && local.has("path") ? local.get("path").getAsString() : "";
        String mappedName = localPath.isEmpty() ? null : transferPaths.get(localPath);
        boolean outgoingUpload = mappedName != null;

        long declaredSize = file.has("size") ? file.get("size").getAsLong() : 0L;
        if (declaredSize <= 0L && file.has("expected_size")) {
            declaredSize = file.get("expected_size").getAsLong();
        }

        long completed = outgoingUpload ? uploaded : downloaded;
        long total = Math.max(declaredSize, completed);
        boolean downloadingDone = local != null && local.has("is_downloading_completed")
                && local.get("is_downloading_completed").getAsBoolean();
        boolean uploadingDone = remote != null && remote.has("is_uploading_completed")
                && remote.get("is_uploading_completed").getAsBoolean();
        boolean done = outgoingUpload ? uploadingDone : downloadingDone;

        if (mappedName != null) transferNames.put(fileId, mappedName);

        String fileName = transferNames.getOrDefault(fileId, "media");
        TelegramTransport.Listener l = listener;
        if (l != null) l.onTransferProgress(new TransferProgress(fileName, completed, total, done));

        if (done && !localPath.isEmpty()) {
            transferPaths.remove(localPath);
            transferNames.remove(fileId);
        }
    }

    private void notifyRequestFailure(CompletableFuture<JsonObject> future) {
        future.whenComplete((ignored, error) -> {
            if (error != null) notifyError(TelegramJsonErrorMapper.fromThrowable(error));
        });
    }

    private void notifyError(TelegramError error) {
        TelegramTransport.Listener l = listener;
        if (l != null && error != null) l.onError(error);
    }

    private void finishClosed() {
        if (!running.compareAndSet(true, false)) return;
        closing.set(true);
        IllegalStateException error = new IllegalStateException("TDLib transport closed");
        for (Map.Entry<Long, CompletableFuture<JsonObject>> entry : pending.entrySet()) {
            CompletableFuture<JsonObject> future = entry.getValue();
            if (pending.remove(entry.getKey(), future)) future.completeExceptionally(error);
        }
        for (ScheduledFuture<?> task : requestTimeouts.values()) task.cancel(false);
        requestTimeouts.clear();
        scheduler.shutdownNow();
        receiver.interrupt();
        closeFuture.complete(null);
    }

    private void shutdownOnFailure(Throwable error) {
        running.set(false);
        closing.set(true);
        for (Map.Entry<Long, CompletableFuture<JsonObject>> entry : pending.entrySet()) {
            CompletableFuture<JsonObject> future = entry.getValue();
            if (pending.remove(entry.getKey(), future)) future.completeExceptionally(error);
        }
        for (ScheduledFuture<?> task : requestTimeouts.values()) task.cancel(false);
        requestTimeouts.clear();
        scheduler.shutdownNow();
        receiver.interrupt();
    }

    private boolean isAllChatsLoaded(Throwable error) {
        Throwable unwrapped = unwrap(error);
        if (!(unwrapped instanceof TelegramRuntimeError)) return false;
        return ((TelegramRuntimeError) unwrapped).tdlibCode == 404;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static RuntimeException propagate(Throwable error) {
        Throwable unwrapped = unwrap(error);
        return unwrapped instanceof RuntimeException
                ? (RuntimeException) unwrapped : new RuntimeException(unwrapped);
    }

    private static int clamp(int value) { return Math.max(1, Math.min(100, value)); }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
        return value;
    }

    private static JsonObject formatted(String text) {
        JsonObject value = new JsonObject();
        value.addProperty("@type", "formattedText");
        value.addProperty("text", text);
        value.add("entities", new JsonArray());
        return value;
    }

    private static String type(JsonObject json) {
        return json != null && json.has("@type") ? json.get("@type").getAsString() : "";
    }

    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private static final class TelegramRuntimeError extends RuntimeException {
        final TelegramError telegramError;
        final int tdlibCode;

        TelegramRuntimeError(TelegramError error) {
            this(error, 0);
        }

        TelegramRuntimeError(TelegramError error, int tdlibCode) {
            super(error == null ? "TDLib error" : error.getMessage(), error);
            this.telegramError = error;
            this.tdlibCode = tdlibCode;
        }
    }

    private static final class NamedDaemonFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicLong counter = new AtomicLong(1L);

        NamedDaemonFactory(String prefix) { this.prefix = prefix; }

        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
