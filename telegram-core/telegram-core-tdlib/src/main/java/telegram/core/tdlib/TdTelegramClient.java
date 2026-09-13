package telegram.core.tdlib;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import telegram.core.api.TelegramAuth;
import telegram.core.api.TelegramClient;
import telegram.core.api.TelegramConfig;
import telegram.core.api.TelegramError;
import telegram.core.api.TelegramFactory;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Production-oriented TDLib JSON client. Telegram is a real user account, not a bot. */
public final class TdTelegramClient implements TelegramClient, TelegramAuth {
    private static final Gson GSON = new Gson();

    private final TelegramConfig config;
    private final TelegramFactory.SessionStore sessionStore;
    private final TdJsonBridge bridge;
    private final int clientId;
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final AtomicLong requestSeq = new AtomicLong(1);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread receiver;
    private volatile AuthState authState = new AuthState(AuthState.Type.UNKNOWN);

    public TdTelegramClient(TelegramConfig config, TelegramFactory.SessionStore sessionStore) {
        this.config = config;
        this.sessionStore = sessionStore;
        this.bridge = TdJsonBridge.load();
        this.clientId = bridge.createClientId();
        this.receiver = new Thread(this::receiveLoop, "telegram-tdlib-receiver");
        this.receiver.setDaemon(true);
        this.receiver.start();
        startAuthorization();
    }

    @Override public CompletableFuture<AuthState> getAuthState() {
        return CompletableFuture.completedFuture(authState);
    }

    @Override public CompletableFuture<AuthState> login() {
        startAuthorization();
        return CompletableFuture.completedFuture(authState);
    }

    @Override public CompletableFuture<Void> logout() {
        return send("logOut", new JsonObject()).thenApply(x -> {
            authState = new AuthState(AuthState.Type.LOGGING_OUT);
            return null;
        });
    }

    @Override public CompletableFuture<List<Chat>> getChats(int limit) {
        JsonObject args = new JsonObject();
        JsonObject list = new JsonObject();
        list.addProperty("@type", "chatListMain");
        args.add("chat_list", list);
        args.addProperty("limit", Math.max(1, Math.min(limit, 100)));
        return send("getChats", args).thenApply(response -> new ChatListDecoder().decode(response));
    }

    @Override public CompletableFuture<List<Message>> getMessages(long chatId, int limit) {
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.addProperty("from_message_id", 0);
        args.addProperty("offset", 0);
        args.addProperty("limit", Math.max(1, Math.min(limit, 100)));
        args.addProperty("only_local", false);
        return send("getChatHistory", args).thenApply(MessageDecoder::decodeList);
    }

    @Override public CompletableFuture<Message> sendText(long chatId, String text) {
        JsonObject formatted = new JsonObject();
        formatted.addProperty("@type", "formattedText");
        formatted.addProperty("text", text == null ? "" : text);
        formatted.add("entities", new com.google.gson.JsonArray());
        JsonObject content = new JsonObject();
        content.addProperty("@type", "inputMessageText");
        content.add("text", formatted);
        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.add("reply_to", com.google.gson.JsonNull.INSTANCE);
        args.add("options", new JsonObject());
        args.add("reply_markup", com.google.gson.JsonNull.INSTANCE);
        args.add("input_message_content", content);
        return send("sendMessage", args).thenApply(MessageDecoder::decode);
    }

    @Override public CompletableFuture<Message> sendPhoto(long chatId, String filePath, String caption) {
        File file = new File(filePath);
        if (!file.isFile() || !file.canRead()) {
            return failed(new IllegalArgumentException("Photo file is not readable: " + file.getAbsolutePath()));
        }

        JsonObject local = new JsonObject();
        local.addProperty("@type", "inputFileLocal");
        local.addProperty("path", file.getAbsolutePath());

        JsonObject photo = new JsonObject();
        photo.addProperty("@type", "inputMessagePhoto");
        photo.add("photo", local);
        photo.add("added_sticker_file_ids", new com.google.gson.JsonArray());
        photo.addProperty("width", 0);
        photo.addProperty("height", 0);
        JsonObject captionJson = new JsonObject();
        captionJson.addProperty("@type", "formattedText");
        captionJson.addProperty("text", caption == null ? "" : caption);
        captionJson.add("entities", new com.google.gson.JsonArray());
        photo.add("caption", captionJson);

        JsonObject args = new JsonObject();
        args.addProperty("chat_id", chatId);
        args.add("reply_to", com.google.gson.JsonNull.INSTANCE);
        args.add("options", new JsonObject());
        args.add("reply_markup", com.google.gson.JsonNull.INSTANCE);
        args.add("input_message_content", photo);
        return send("sendMessage", args).thenApply(MessageDecoder::decode);
    }

    @Override public void addListener(EventListener listener) { if (listener != null) listeners.addIfAbsent(listener); }
    @Override public void removeListener(EventListener listener) { listeners.remove(listener); }

    @Override public CompletableFuture<AuthState> state() {
        return CompletableFuture.completedFuture(authState);
    }

    @Override public CompletableFuture<Void> submitPhoneNumber(String phoneNumber) {
        JsonObject args = new JsonObject();
        args.addProperty("phone_number", phoneNumber);
        JsonObject settings = new JsonObject();
        settings.addProperty("@type", "phoneNumberAuthenticationSettings");
        settings.addProperty("allow_flash_call", false);
        settings.addProperty("allow_missed_call", false);
        settings.addProperty("is_current_phone_number", true);
        settings.addProperty("allow_sms_retriever_api", false);
        args.add("settings", settings);
        return send("setAuthenticationPhoneNumber", args).thenApply(x -> null);
    }

    @Override public CompletableFuture<Void> submitCode(String code) {
        JsonObject args = new JsonObject();
        args.addProperty("code", code);
        return send("checkAuthenticationCode", args).thenApply(x -> null);
    }

    @Override public CompletableFuture<Void> submitPassword(String password) {
        JsonObject args = new JsonObject();
        args.addProperty("password", password);
        return send("checkAuthenticationPassword", args).thenApply(x -> null);
    }

    public void startAuthorization() {
        JsonObject params = new JsonObject();
        params.addProperty("@type", "setTdlibParameters");
        params.addProperty("database_directory", config.databaseDirectory);
        params.addProperty("files_directory", config.filesDirectory);
        params.addProperty("use_message_database", true);
        params.addProperty("use_secret_chats", true);
        params.addProperty("api_id", config.apiId);
        params.addProperty("api_hash", config.apiHash);
        params.addProperty("system_language_code", Locale.getDefault().getLanguage());
        params.addProperty("device_model", config.deviceModel);
        params.addProperty("system_version", System.getProperty("os.name", "unknown"));
        params.addProperty("application_version", config.applicationVersion);
        params.addProperty("enable_storage_optimizer", true);
        params.addProperty("use_test_dc", false);
        bridge.send(clientId, GSON.toJson(params));
    }

    public void close() {
        if (running.compareAndSet(true, false)) receiver.interrupt();
    }

    private CompletableFuture<JsonObject> send(String type, JsonObject args) {
        if (!running.get()) return failed(new IllegalStateException("Telegram client closed"));
        long requestId = requestSeq.getAndIncrement();
        JsonObject request = args.deepCopy();
        request.addProperty("@type", type);
        request.addProperty("@extra", Long.toString(requestId));
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(requestId, future);
        try {
            bridge.send(clientId, GSON.toJson(request));
        } catch (RuntimeException e) {
            pending.remove(requestId);
            future.completeExceptionally(e);
        }
        return future;
    }

    private void receiveLoop() {
        while (running.get()) {
            try {
                String json = bridge.receive(1.0);
                if (json == null || json.isEmpty()) continue;
                JsonObject update = JsonParser.parseString(json).getAsJsonObject();
                handle(update);
            } catch (Throwable error) {
                for (EventListener listener : listeners) listener.onError(error);
            }
        }
    }

    private void handle(JsonObject update) {
        if (update.has("@extra")) {
            try {
                long requestId = Long.parseLong(update.get("@extra").getAsString());
                CompletableFuture<JsonObject> future = pending.remove(requestId);
                if (future != null) {
                    if ("error".equals(update.get("@type").getAsString())) {
                        String message = update.has("message") ? update.get("message").getAsString() : "TDLib error";
                        future.completeExceptionally(new TelegramRuntimeException(TelegramError.Code.UNKNOWN, message));
                    } else future.complete(update);
                    return;
                }
            } catch (RuntimeException ignored) { }
        }

        String type = update.has("@type") ? update.get("@type").getAsString() : "";
        if ("updateAuthorizationState".equals(type)) handleAuth(update.getAsJsonObject("authorization_state"));
        else if ("updateNewMessage".equals(type)) {
            Message message = MessageDecoder.decode(update.getAsJsonObject("message"));
            for (EventListener listener : listeners) listener.onMessage(message);
        }
    }

    private void handleAuth(JsonObject state) {
        if (state == null) return;
        String type = state.get("@type").getAsString();
        AuthState.Type mapped;
        switch (type) {
            case "authorizationStateWaitPhoneNumber": mapped = AuthState.Type.WAIT_PHONE; break;
            case "authorizationStateWaitCode": mapped = AuthState.Type.WAIT_CODE; break;
            case "authorizationStateWaitPassword": mapped = AuthState.Type.WAIT_PASSWORD; break;
            case "authorizationStateReady": mapped = AuthState.Type.READY; break;
            case "authorizationStateLoggingOut": mapped = AuthState.Type.LOGGING_OUT; break;
            case "authorizationStateClosed": mapped = AuthState.Type.CLOSED; break;
            default: mapped = AuthState.Type.UNKNOWN;
        }
        authState = new AuthState(mapped);
        for (EventListener listener : listeners) listener.onAuthStateChanged(authState);
    }

    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private static final class TelegramRuntimeException extends RuntimeException {
        final TelegramError.Code code;
        TelegramRuntimeException(TelegramError.Code code, String message) { super(message); this.code = code; }
    }
}
