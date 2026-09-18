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
import telegram.core.api.TelegramTransport;
import telegram.core.api.TransferProgress;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TDLib JSON transport for a normal Telegram user account.
 * The host application only sees TelegramTransport; TDLib details stay here.
 */
public final class TdLibTransport implements TelegramTransport, AutoCloseable {
    private static final Gson GSON = new Gson();

    private final TdLibTransportConfig config;
    private final TdJsonBridge td;
    private final int clientId;
    private final AtomicLong requestIds = new AtomicLong(1L);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> transferNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> transferPaths = new ConcurrentHashMap<>();
    private final Thread receiver;
    private volatile TelegramTransport.Listener listener;
    private volatile AuthorizationState state = new AuthorizationState(AuthorizationState.Type.UNKNOWN);

    public TdLibTransport(TdLibTransportConfig config) {
        if (config == null) throw new IllegalArgumentException("config required");
        this.config = config;
        this.td = TdJsonBridge.load();
        this.clientId = td.createClientId();
        this.receiver = new Thread(this::receiveLoop, "tdlib-transport-receiver");
        this.receiver.setDaemon(true);
        this.receiver.start();
    }

    @Override public void setListener(TelegramTransport.Listener listener) {
        this.listener = listener;
    }

    @Override public CompletableFuture<AuthorizationState> authorizationState() {
        return CompletableFuture.completedFuture(state);
    }

    @Override public CompletableFuture<Void> requestQrCodeAuthentication() {
        return request("requestQrCodeAuthentication", new JsonObject()).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setPhoneNumber(String phoneNumber) {
        JsonObject settings = new JsonObject();
        settings.addProperty("@type", "phoneNumberAuthenticationSettings");
        settings.addProperty("allow_flash_call", false);
        settings.addProperty("allow_missed_call", false);
        settings.addProperty("is_current_phone_number", true);
        settings.addProperty("allow_sms_retriever_api", false);
        JsonObject args = new JsonObject();
        args.addProperty("phone_number", required(phoneNumber, "phoneNumber"));
        args.add("settings", settings);
        return request("setAuthenticationPhoneNumber", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setEmailAddress(String emailAddress) {
        JsonObject args = new JsonObject();
        args.addProperty("email_address", required(emailAddress, "emailAddress"));
        return request("setAuthenticationEmailAddress", args).thenApply(v -> null);
    }

    @Override public CompletableFuture<Void> setEmailCode(String code) {
        JsonObject auth = new JsonObject();
        auth.addProperty("@type", "emailAddressAuthentication");
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
        JsonObject args = new JsonObject();
        args.add("reason", JsonNull.INSTANCE);
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
        if (!running.compareAndSet(true, false)) return CompletableFuture.completedFuture(null);
        try { td.send(clientId, "{\"@type\":\"close\"}"); } catch (RuntimeException ignored) { }
        receiver.interrupt();
        IllegalStateException error = new IllegalStateException("TDLib transport closed");
        for (CompletableFuture<JsonObject> future : pending.values()) future.completeExceptionally(error);
        pending.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletableFuture<Page<Chat>> chats(int limit, long cursor) {
        final int safeLimit = clamp(limit);
        final long safeCursor = Math.max(0L, cursor);
        final int needed = (int) Math.min(1000L, safeCursor + safeLimit + 1L);
        JsonObject list = new JsonObject();
        list.addProperty("@type", "chatListMain");
        JsonObject loadArgs = new JsonObject();
        loadArgs.add("chat_list", list);
        loadArgs.addProperty("limit", needed);
        return request("loadChats", loadArgs).handle((ignored, error) -> null).thenCompose(ignored -> {
            JsonObject args = new JsonObject();
            args.add("chat_list", list);
            args.addProperty("limit", needed);
            return request("getChats", args);
        }).thenCompose(response -> {
            JsonArray ids = response.has("chat_ids") ? response.getAsJsonArray("chat_ids") : new JsonArray();
            int start = (int) Math.min((long) ids.size(), safeCursor);
            int end = Math.min(ids.size(), start + safeLimit);
            List<CompletableFuture<Chat>> futures = new ArrayList<>();
            for (int i = start; i < end; i++) futures.add(getChat(ids.get(i).getAsLong()));
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> {
                List<Chat> result = new ArrayList<>(futures.size());
                for (CompletableFuture<Chat> future : futures) result.add(future.join());
                return new Page<>(result, safeCursor + result.size(), ids.size() > end);
            });
        });
    }

    private CompletableFuture<Chat> getChat(long chatId) {
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        return request("getChat", args).thenApply(json -> {
            long lastMessageId = 0L;
            if (json.has("last_message") && json.get("last_message").isJsonObject()) {
                JsonObject last = json.getAsJsonObject("last_message");
                if (last.has("id")) lastMessageId = last.get("id").getAsLong();
            }
            return new Chat(chatId, json.has("title") ? json.get("title").getAsString() : "", lastMessageId);
        });
    }

    @Override public CompletableFuture<Page<Message>> messages(long chatId, int limit, long fromMessageId) {
        if (chatId == 0L) return failed(new IllegalArgumentException("chatId required"));
        int safeLimit = clamp(limit);
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.addProperty("from_message_id", Math.max(0L, fromMessageId));
        args.addProperty("offset", 0);
        args.addProperty("limit", safeLimit);
        args.addProperty("only_local", false);
        return request("getChatHistory", args).thenApply(response -> {
            List<Message> result = MessageJson.decodeList(response);
            long next = result.isEmpty() ? Math.max(0L, fromMessageId) : result.get(result.size() - 1).id;
            return new Page<>(result, next, result.size() == safeLimit);
        });
    }

    @Override public CompletableFuture<Message> sendText(long chatId, String text, SendOptions options) {
        if (chatId == 0L) return failed(new IllegalArgumentException("chatId required"));
        JsonObject content = new JsonObject();
        content.addProperty("@type", "inputMessageText");
        content.add("text", formatted(required(text, "text")));
        JsonObject args = sendBase(chatId, options);
        args.add("input_message_content", content);
        return request("sendMessage", args).thenApply(MessageJson::decode);
    }

    @Override public CompletableFuture<Message> sendMedia(long chatId, MediaSpec spec, SendOptions options) {
        if (chatId == 0L) return failed(new IllegalArgumentException("chatId required"));
        if (spec == null || spec.path == null || spec.path.trim().isEmpty()) return failed(new IllegalArgumentException("media path required"));
        File file = new File(spec.path);
        if (!file.isFile() || !file.canRead()) return failed(new IllegalArgumentException("media file not readable: " + file.getAbsolutePath()));

        String mime = spec.mimeType == null ? "" : spec.mimeType.toLowerCase(Locale.ROOT);
        JsonObject input = new JsonObject();
        input.addProperty("@type", "inputFileLocal");
        input.addProperty("path", file.getAbsolutePath());
        JsonObject content = new JsonObject();
        if (mime.startsWith("image/")) {
            content.addProperty("@type", "inputMessagePhoto");
            content.add("photo", input);
            content.add("thumbnail", JsonNull.INSTANCE);
            content.add("added_sticker_file_ids", new JsonArray());
            content.addProperty("width", 0);
            content.addProperty("height", 0);
            content.add("caption", formatted(options == null ? "" : options.caption));
            content.addProperty("show_caption_above_media", false);
            content.add("self_destruct_type", JsonNull.INSTANCE);
            content.addProperty("has_spoiler", false);
        } else if (mime.startsWith("video/")) {
            content.addProperty("@type", "inputMessageVideo");
            content.add("video", input);
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
            content.addProperty("@type", "inputMessageDocument");
            content.add("document", input);
            content.add("thumbnail", JsonNull.INSTANCE);
            content.addProperty("disable_content_type_detection", false);
            content.add("caption", formatted(options == null ? "" : options.caption));
        }
        JsonObject args = sendBase(chatId, options);
        args.add("input_message_content", content);
        transferPaths.put(file.getAbsolutePath(), spec.fileName == null || spec.fileName.trim().isEmpty() ? file.getName() : spec.fileName);
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
            sendOptions.addProperty("effect_id", 0);
            sendOptions.addProperty("sending_id", 0);
            sendOptions.addProperty("only_preview", false);
            args.add("options", sendOptions);
        }
        args.add("reply_markup", JsonNull.INSTANCE);
        return args;
    }

    private CompletableFuture<JsonObject> request(String type, JsonObject args) {
        if (!running.get()) return failed(new IllegalStateException("TDLib transport closed"));
        long id = requestIds.getAndIncrement();
        JsonObject request = args == null ? new JsonObject() : args.deepCopy();
        request.addProperty("@type", type);
        request.addProperty("@extra", Long.toString(id));
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, future);
        try { td.send(clientId, GSON.toJson(request)); }
        catch (RuntimeException e) { pending.remove(id); future.completeExceptionally(e); }
        return future;
    }

    private void receiveLoop() {
        while (running.get()) {
            try {
                String raw = td.receive(1.0);
                if (raw == null || raw.isEmpty()) continue;
                JsonObject update = JsonParser.parseString(raw).getAsJsonObject();
                handle(update);
            } catch (Throwable error) {
                TelegramTransport.Listener l = listener;
                if (l != null) l.onError(asError(error));
            }
        }
    }

    private void handle(JsonObject update) {
        if (update.has("@extra")) {
            try {
                long id = Long.parseLong(update.get("@extra").getAsString());
                CompletableFuture<JsonObject> future = pending.remove(id);
                if (future != null) {
                    if ("error".equals(type(update))) future.completeExceptionally(asRuntimeError(update));
                    else future.complete(update);
                    return;
                }
            } catch (RuntimeException ignored) { }
        }

        String type = type(update);
        if ("updateAuthorizationState".equals(type)) {
            handleAuth(update.getAsJsonObject("authorization_state"));
        } else if ("updateNewMessage".equals(type)) {
            TelegramTransport.Listener l = listener;
            if (l != null) l.onMessage(MessageJson.decode(update.getAsJsonObject("message")));
        } else if ("updateFile".equals(type)) {
            handleFile(update.getAsJsonObject("file"));
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
            p.addProperty("database_encryption_key", config.databaseEncryptionKey);
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
            request("setTdlibParameters", p);
        } else if ("authorizationStateWaitEncryptionKey".equals(authType)) {
            JsonObject p = new JsonObject();
            p.addProperty("encryption_key", config.databaseEncryptionKey);
            request("checkDatabaseEncryptionKey", p);
        }

        AuthorizationState.Type mapped;
        switch (authType) {
            case "authorizationStateWaitPhoneNumber": mapped = AuthorizationState.Type.WAIT_PHONE; break;
            case "authorizationStateWaitPremiumPurchase": mapped = AuthorizationState.Type.WAIT_PREMIUM_PURCHASE; break;
            case "authorizationStateWaitOtherDeviceConfirmation": mapped = AuthorizationState.Type.WAIT_OTHER_DEVICE_CONFIRMATION; break;
            case "authorizationStateWaitEmailAddress": mapped = AuthorizationState.Type.WAIT_EMAIL; break;
            case "authorizationStateWaitEmailCode": mapped = AuthorizationState.Type.WAIT_EMAIL_CODE; break;
            case "authorizationStateWaitCode": mapped = AuthorizationState.Type.WAIT_CODE; break;
            case "authorizationStateWaitPassword": mapped = AuthorizationState.Type.WAIT_PASSWORD; break;
            case "authorizationStateWaitRegistration": mapped = AuthorizationState.Type.WAIT_REGISTRATION; break;
            case "authorizationStateLoggingOut": mapped = AuthorizationState.Type.LOGGING_OUT; break;
            case "authorizationStateClosing": mapped = AuthorizationState.Type.CLOSING; break;
            case "authorizationStateClosed": mapped = AuthorizationState.Type.CLOSED; break;
            case "authorizationStateReady": mapped = AuthorizationState.Type.READY; break;
            default: mapped = AuthorizationState.Type.UNKNOWN;
        }
        state = new AuthorizationState(mapped, authType);
        TelegramTransport.Listener l = listener;
        if (l != null) l.onAuthorizationState(state);
    }

    private void handleFile(JsonObject file) {
        if (file == null) return;
        int fileId = file.has("id") ? file.get("id").getAsInt() : 0;
        JsonObject local = file.has("local") && file.get("local").isJsonObject() ? file.getAsJsonObject("local") : null;
        long downloaded = local != null && local.has("downloaded_size") ? local.get("downloaded_size").getAsLong() : 0L;
        long uploaded = local != null && local.has("uploaded_size") ? local.get("uploaded_size").getAsLong() : 0L;
        long completed = Math.max(downloaded, uploaded);
        long total = file.has("size") ? file.get("size").getAsLong() : 0L;
        boolean done = (local != null && local.has("is_downloading_completed") && local.get("is_downloading_completed").getAsBoolean())
                || (local != null && local.has("is_uploading_completed") && local.get("is_uploading_completed").getAsBoolean());
        if (local != null && local.has("path")) {
            String path = local.get("path").getAsString();
            String name = transferPaths.get(path);
            if (name != null) transferNames.put(fileId, name);
        }
        String fileName = transferNames.getOrDefault(fileId, "media");
        TelegramTransport.Listener l = listener;
        if (l != null) l.onTransferProgress(new TransferProgress(fileName, completed, total, done));
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
    private static String type(JsonObject json) { return json.has("@type") ? json.get("@type").getAsString() : ""; }
    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
    private static TelegramError asError(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        return new TelegramError(TelegramError.Code.INTERNAL, message);
    }
    private static TelegramRuntimeError asRuntimeError(JsonObject json) {
        return new TelegramRuntimeError(asError(json));
    }
    private static TelegramError asError(JsonObject json) {
        int code = json.has("code") ? json.get("code").getAsInt() : 0;
        String message = json.has("message") ? json.get("message").getAsString() : "TDLib error";
        String lower = message.toLowerCase(Locale.ROOT);
        TelegramError.Code mapped = code == 401 ? TelegramError.Code.AUTH_REQUIRED
                : code == 429 ? TelegramError.Code.RATE_LIMITED
                : lower.contains("network") ? TelegramError.Code.NETWORK
                : lower.contains("permission") ? TelegramError.Code.PERMISSION_DENIED
                : TelegramError.Code.INTERNAL;
        return new TelegramError(mapped, message);
    }

    private static final class TelegramRuntimeError extends RuntimeException {
        TelegramRuntimeError(TelegramError error) { super(error.getMessage(), error); }
    }
}
