package telegram.core.tdlib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

/** TDLib JSON transport. No Bot API is used; authorization is for a normal Telegram user account. */
public final class TdLibTransport implements TelegramTransport, AutoCloseable {
    private static final Gson GSON = new Gson();
    private final TdLibTransportConfig config;
    private final TdJsonBridge td;
    private final int clientId;
    private final AtomicLong ids = new AtomicLong(1);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ConcurrentHashMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> transferNames = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PendingMedia> media = new CopyOnWriteArrayList<>();
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

    @Override public void setListener(TelegramTransport.Listener listener) { this.listener = listener; }
    @Override public CompletableFuture<AuthorizationState> authorizationState() { return CompletableFuture.completedFuture(state); }

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
        JsonObject codeObject = new JsonObject();
        codeObject.addProperty("@type", "emailAddressAuthentication");
        codeObject.addProperty("code", required(code, "emailCode"));
        JsonObject args = new JsonObject();
        args.add("code", codeObject);
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

    @Override public CompletableFuture<Void> close() {
        if (!running.compareAndSet(true, false)) return CompletableFuture.completedFuture(null);
        try { td.send(clientId, "{\"@type\":\"close\"}"); } catch (RuntimeException ignored) { }
        receiver.interrupt();
        for (CompletableFuture<JsonObject> future : pending.values()) future.completeExceptionally(new IllegalStateException("TDLib transport closed"));
        pending.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletableFuture<Page<Chat>> chats(int limit, long cursor) {
        int safeLimit = clamp(limit);
        long safeCursor = Math.max(0L, cursor);
        int needed = (int) Math.min(1000L, safeCursor + safeLimit + 1L);
        JsonObject load = new JsonObject();
        JsonObject list = new JsonObject();
        list.addProperty("@type", "chatListMain");
        load.add("chat_list", list);
        load.addProperty("limit", needed);
        return request("loadChats", load).handle((ignored, error) -> null)
            .thenCompose(ignored -> {
                JsonObject args = new JsonObject();
                args.add("chat_list", list);
                args.addProperty("limit", needed);
                return request("getChats", args);
            }).thenCompose(response -> {
                JsonArray ids = response.has("chat_ids") ? response.getAsJsonArray("chat_ids") : new JsonArray();
                int start = (int) Math.min(ids.size(), safeCursor);
                int end = Math.min(ids.size(), start + safeLimit);
                List<CompletableFuture<Chat>> futures = new ArrayList<>();
                for (int i = start; i < end; i++) futures.add(getChat(ids.get(i).getAsLong()));
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> {
                    List<Chat> result = new ArrayList<>(futures.size());
                    for (CompletableFuture<Chat> f : futures) result.add(f.join());
                    boolean more = ids.size() > end;
                    return new Page<>(result, safeCursor + result.size(), more);
                });
            });
    }

    private CompletableFuture<Chat> getChat(long chatId) {
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        return request("getChat", args).thenApply(json -> new Chat(
            chatId,
            json.has("title") ? json.get("title").getAsString() : Long.toString(chatId),
            json.has("last_message") && json.get("last_message").isJsonObject() ? json.getAsJsonObject("last_message").get("id").getAsLong() : 0L
        ));
    }

    @Override public CompletableFuture<Page<Message>> messages(long chatId, int limit, long fromMessageId) {
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.addProperty("from_message_id", Math.max(0L, fromMessageId));
        args.addProperty("offset", 0);
        args.addProperty("limit", clamp(limit));
        args.addProperty("only_local", false);
        return request("getChatHistory", args).thenApply(json -> {
            List<Message> result = MessageJson.decodeList(json);
            long next = result.isEmpty() ? fromMessageId : result.get(result.size() - 1).id;
            return new Page<>(result, next, result.size() == clamp(limit));
        });
    }

    @Override public CompletableFuture<Message> sendText(long chatId, String text, SendOptions options) {
        JsonObject formatted = formatted(text);
        JsonObject content = new JsonObject();
        content.addProperty("@type", "inputMessageText");
        content.add("text", formatted);
        JsonObject args = sendBase(chatId, options);
        args.add("input_message_content", content);
        return request("sendMessage", args).thenApply(MessageJson::decode);
    }

    @Override public CompletableFuture<Message> sendMedia(long chatId, MediaSpec spec, SendOptions options) {
        if (spec == null || spec.path == null || spec.path.trim().isEmpty()) return failed(new IllegalArgumentException("media path required"));
        File file = new File(spec.path);
        if (!file.isFile() || !file.canRead()) return failed(new IllegalArgumentException("media file not readable: " + file.getAbsolutePath()));
        JsonObject input = new JsonObject();
        input.addProperty("@type", "inputFileLocal");
        input.addProperty("path", file.getAbsolutePath());
        JsonObject content = new JsonObject();
        String mime = spec.mimeType == null ? "" : spec.mimeType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) {
            content.addProperty("@type", "inputMessagePhoto");
            content.add("photo", input);
            content.add("added_sticker_file_ids", new JsonArray());
            content.add("caption", formatted(options == null ? "" : options.caption));
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
        media.add(new PendingMedia(file.getAbsolutePath(), spec.fileName));
        return request("sendMessage", args).thenApply(MessageJson::decode);
    }

    private JsonObject sendBase(long chatId, SendOptions options) {
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.add("topic_id", JsonNull.INSTANCE);
        args.add("reply_to", JsonNull.INSTANCE);
        JsonObject sendOptions = new JsonObject();
        sendOptions.addProperty("@type", "messageSendOptions");
        sendOptions.addProperty("disable_notification", options != null && options.disableNotification);
        sendOptions.addProperty("protect_content", false);
        sendOptions.addProperty("update_order_of_installed_sticker_sets", false);
        args.add("options", sendOptions);
        args.add("reply_markup", JsonNull.INSTANCE);
        return args;
    }

    private JsonObject formatted(String text) {
        JsonObject formatted = new JsonObject();
        formatted.addProperty("@type", "formattedText");
        formatted.addProperty("text", text == null ? "" : text);
        formatted.add("entities", new JsonArray());
        return formatted;
    }

    private CompletableFuture<JsonObject> request(String type, JsonObject args) {
        if (!running.get()) return failed(new IllegalStateException("TDLib transport closed"));
        long id = ids.getAndIncrement();
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
                String json = td.receive(1.0);
                if (json == null || json.isEmpty()) continue;
                JsonObject update = JsonParser.parseString(json).getAsJsonObject();
                handle(update);
            } catch (Throwable error) {
                TelegramTransport.Listener l = listener;
                if (l != null) l.onError(error(error));
            }
        }
    }

    private void handle(JsonObject update) {
        if (update.has("@extra")) {
            try {
                long id = Long.parseLong(update.get("@extra").getAsString());
                CompletableFuture<JsonObject> future = pending.remove(id);
                if (future != null) {
                    if ("error".equals(type(update))) future.completeExceptionally(new TdException(toError(update)));
                    else future.complete(update);
                    return;
                }
            } catch (RuntimeException ignored) { }
        }

        String type = type(update);
        if ("updateAuthorizationState".equals(type)) handleAuth(update.getAsJsonObject("authorization_state"));
        else if ("updateNewMessage".equals(type)) {
            Message message = MessageJson.decode(update.getAsJsonObject("message"));
            TelegramTransport.Listener l = listener;
            if (l != null) l.onMessage(message);
        } else if ("updateFile".equals(type)) handleFile(update);
    }

    private void handleAuth(JsonObject auth) {
        if (auth == null || !auth.has("@type")) return;
        String type = auth.get("@type").getAsString();
        if ("authorizationStateWaitTdlibParameters".equals(type)) {
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
        } else if ("authorizationStateWaitEncryptionKey".equals(type)) {
            JsonObject p = new JsonObject();
            p.addProperty("encryption_key", config.databaseEncryptionKey);
            request("checkDatabaseEncryptionKey", p);
        }
        AuthorizationState.Type mapped;
        switch (type) {
            case "authorizationStateWaitPhoneNumber": mapped = AuthorizationState.Type.WAIT_PHONE; break;
            case "authorizationStateWaitEmailAddress": mapped = AuthorizationState.Type.WAIT_EMAIL; break;
            case "authorizationStateWaitEmailCode": mapped = AuthorizationState.Type.WAIT_EMAIL_CODE; break;
            case "authorizationStateWaitCode": mapped = AuthorizationState.Type.WAIT_CODE; break;
            case "authorizationStateWaitPassword": mapped = AuthorizationState.Type.WAIT_PASSWORD; break;
            case "authorizationStateWaitRegistration": mapped = AuthorizationState.Type.WAIT_REGISTRATION; break;
            case "authorizationStateReady": mapped = AuthorizationState.Type.READY; break;
            case "authorizationStateClosing": mapped = AuthorizationState.Type.CLOSING; break;
            case "authorizationStateClosed": mapped = AuthorizationState.Type.CLOSED; break;
            default: mapped = AuthorizationState.Type.UNKNOWN;
        }
        state = new AuthorizationState(mapped, type);
        TelegramTransport.Listener l = listener;
        if (l != null) l.onAuthorizationState(state);
    }

    private void handleFile(JsonObject update) {
        if (!update.has("file")) return;
        JsonObject file = update.getAsJsonObject("file");
        int fileId = file.has("id") ? file.get("id").getAsInt() : 0;
        JsonObject local = file.has("local") ? file.getAsJsonObject("local") : null;
        long downloaded = 0L, expected = 0L;
        if (local != null) {
            if (local.has("downloaded_size")) downloaded = local.get("downloaded_size").getAsLong();
            if (local.has("downloaded_size")) expected = local.get("downloaded_size").getAsLong();
        }
        String name = transferNames.getOrDefault(fileId, "media");
        TelegramTransport.Listener l = listener;
        if (l != null) l.onTransferProgress(new TransferProgress(name, downloaded, expected, local != null && local.has("is_downloading_completed") && local.get("is_downloading_completed").getAsBoolean()));
    }

    private static int clamp(int n) { return Math.max(1, Math.min(100, n)); }
    private static String required(String value, String name) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required"); return value; }
    private static <T> CompletableFuture<T> failed(Throwable error) { CompletableFuture<T> f = new CompletableFuture<>(); f.completeExceptionally(error); return f; }
    private static String type(JsonObject x) { return x.has("@type") ? x.get("@type").getAsString() : ""; }
    private TelegramError error(JsonObject json) { return toError(json); }
    private static TelegramError toError(JsonObject json) {
        int code = json.has("code") ? json.get("code").getAsInt() : 0;
        String message = json.has("message") ? json.get("message").getAsString() : "TDLib error";
        TelegramError.Code mapped = code == 401 ? TelegramError.Code.AUTH_REQUIRED : code == 429 ? TelegramError.Code.RATE_LIMITED : TelegramError.Code.UNKNOWN;
        if (message.toLowerCase(Locale.ROOT).contains("network")) mapped = TelegramError.Code.NETWORK;
        return new TelegramError(mapped, message);
    }

    private static final class TdException extends RuntimeException {
        TdException(TelegramError error) { super(error.getMessage(), error); }
    }

    private static final class PendingMedia {
        final String path; final String fileName;
        PendingMedia(String path, String fileName) { this.path = path; this.fileName = fileName; }
    }
}
