package telegram.core.internal;

import telegram.core.api.AuthorizationState;
import telegram.core.api.Chat;
import telegram.core.api.MediaSpec;
import telegram.core.api.Message;
import telegram.core.api.Page;
import telegram.core.api.SendOptions;
import telegram.core.api.TelegramClient;
import telegram.core.api.TelegramError;
import telegram.core.api.TelegramTransport;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Core orchestration layer. Only this layer knows the public API-to-transport mapping. */
public final class DefaultTelegramClient implements TelegramClient {
    private final TelegramTransport transport;
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<EventListener>();
    private volatile AuthorizationState state = new AuthorizationState(AuthorizationState.Type.UNKNOWN);

    public DefaultTelegramClient(TelegramTransport transport) {
        if (transport == null) throw new IllegalArgumentException("transport required");
        this.transport = transport;
        transport.setListener(new TelegramTransport.Listener() {
            @Override public void onAuthorizationState(AuthorizationState value) {
                state = value;
                for (EventListener listener : listeners) listener.onAuthStateChanged(value);
            }
            @Override public void onMessage(Message message) {
                for (EventListener listener : listeners) listener.onMessage(message);
            }
            @Override public void onError(TelegramError error) {
                for (EventListener listener : listeners) listener.onError(error);
            }
        });
    }

    @Override public CompletableFuture<AuthorizationState> getAuthState() {
        return transport.authorizationState().thenApply(value -> { state = value; return value; });
    }
    @Override public CompletableFuture<Void> submitPhoneNumber(String phoneNumber) {
        requireText(phoneNumber, "phoneNumber");
        return transport.setPhoneNumber(phoneNumber);
    }
    @Override public CompletableFuture<Void> submitCode(String code) {
        requireText(code, "code");
        return transport.setCode(code);
    }
    @Override public CompletableFuture<Void> submitPassword(String password) {
        requireText(password, "password");
        return transport.setPassword(password);
    }
    @Override public CompletableFuture<Void> logout() { return transport.close(); }
    @Override public CompletableFuture<Page<Chat>> getChats(int limit, long cursor) {
        return transport.chats(clamp(limit), cursor);
    }
    @Override public CompletableFuture<Page<Message>> getMessages(long chatId, int limit, long fromMessageId) {
        if (chatId == 0) return failed(new IllegalArgumentException("chatId required"));
        return transport.messages(chatId, clamp(limit), fromMessageId);
    }
    @Override public CompletableFuture<Message> sendText(long chatId, String text, SendOptions options) {
        if (chatId == 0) return failed(new IllegalArgumentException("chatId required"));
        requireText(text, "text");
        return transport.sendText(chatId, text, options == null ? new SendOptions("", false) : options);
    }
    @Override public CompletableFuture<Message> sendMedia(long chatId, MediaSpec media, SendOptions options) {
        if (chatId == 0) return failed(new IllegalArgumentException("chatId required"));
        if (media == null) return failed(new IllegalArgumentException("media required"));
        return transport.sendMedia(chatId, media, options == null ? new SendOptions("", false) : options);
    }
    @Override public void addListener(EventListener listener) { if (listener != null) listeners.addIfAbsent(listener); }
    @Override public void removeListener(EventListener listener) { if (listener != null) listeners.remove(listener); }

    private static int clamp(int limit) { return Math.max(1, Math.min(100, limit)); }
    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
    }
    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(error);
        return future;
    }
}
