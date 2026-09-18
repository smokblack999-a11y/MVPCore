package telegram.core.internal;

import telegram.core.api.AuthorizationState;
import telegram.core.api.Chat;
import telegram.core.api.MediaSpec;
import telegram.core.api.Message;
import telegram.core.api.Page;
import telegram.core.api.SendOptions;
import telegram.core.api.TelegramClient;
import telegram.core.api.TelegramError;
import telegram.core.api.TransferProgress;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Internal adapter from the stable public API to a transport implementation. */
public final class DefaultTelegramClient implements TelegramClient {
    private final TelegramTransport transport;
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<EventListener>();
    private volatile AuthorizationState state = new AuthorizationState(AuthorizationState.Type.UNKNOWN);

    public DefaultTelegramClient(TelegramTransport transport) {
        if (transport == null) throw new IllegalArgumentException("transport required");
        this.transport = transport;
        transport.setListener(new TelegramTransport.Listener() {
            @Override public void onAuthorizationState(AuthorizationState value) {
                if (value == null) return;
                state = value;
                for (EventListener listener : listeners) listener.onAuthStateChanged(value);
            }
            @Override public void onMessage(Message message) {
                for (EventListener listener : listeners) listener.onMessage(message);
            }
            @Override public void onTransferProgress(TransferProgress progress) {
                for (EventListener listener : listeners) listener.onTransferProgress(progress);
            }
            @Override public void onError(TelegramError error) {
                for (EventListener listener : listeners) listener.onError(error);
            }
        });
        transport.authorizationState().thenAccept(value -> {
            if (value != null && value.type != AuthorizationState.Type.UNKNOWN) state = value;
        });
    }

    @Override public CompletableFuture<AuthorizationState> getAuthState() { return CompletableFuture.completedFuture(state); }
    @Override public CompletableFuture<Void> submitPhoneNumber(String phoneNumber) { return transport.setPhoneNumber(requireText(phoneNumber, "phoneNumber")); }
    @Override public CompletableFuture<Void> requestQrCodeAuthentication() { return transport.requestQrCodeAuthentication(); }
    @Override public CompletableFuture<Void> submitEmailAddress(String emailAddress) { return transport.setEmailAddress(requireText(emailAddress, "emailAddress")); }
    @Override public CompletableFuture<Void> submitEmailCode(String code) { return transport.setEmailCode(requireText(code, "code")); }
    @Override public CompletableFuture<Void> submitCode(String code) { return transport.setCode(requireText(code, "code")); }
    @Override public CompletableFuture<Void> resendCode() { return transport.resendCode(); }
    @Override public CompletableFuture<Void> submitPassword(String password) { return transport.setPassword(requireText(password, "password")); }
    @Override public CompletableFuture<Void> register(String firstName, String lastName) {
        return transport.register(requireText(firstName, "firstName"), lastName == null ? "" : lastName);
    }
    @Override public CompletableFuture<Void> logout() { return transport.logout(); }
    @Override public CompletableFuture<Void> close() { return transport.close(); }
    @Override public CompletableFuture<Page<Chat>> getChats(int limit, long cursor) { return transport.chats(clamp(limit), Math.max(0L, cursor)); }
    @Override public CompletableFuture<Page<Message>> getMessages(long chatId, int limit, long fromMessageId) {
        if (chatId == 0) return failed(new IllegalArgumentException("chatId required"));
        return transport.messages(chatId, clamp(limit), Math.max(0L, fromMessageId));
    }
    @Override public CompletableFuture<Message> sendText(long chatId, String text, SendOptions options) {
        if (chatId == 0) return failed(new IllegalArgumentException("chatId required"));
        return transport.sendText(chatId, requireText(text, "text"), options == null ? new SendOptions("", false) : options);
    }
    @Override public CompletableFuture<Message> sendMedia(long chatId, MediaSpec media, SendOptions options) {
        if (chatId == 0) return failed(new IllegalArgumentException("chatId required"));
        if (media == null) return failed(new IllegalArgumentException("media required"));
        return transport.sendMedia(chatId, media, options == null ? new SendOptions("", false) : options);
    }
    @Override public void addListener(EventListener listener) {
        if (listener == null) return;
        listeners.addIfAbsent(listener);
        AuthorizationState currentState = state;
        if (currentState != null && currentState.type != AuthorizationState.Type.UNKNOWN) {
            listener.onAuthStateChanged(currentState);
        }
    }
    @Override public void removeListener(EventListener listener) { if (listener != null) listeners.remove(listener); }

    private static int clamp(int limit) { return Math.max(1, Math.min(100, limit)); }
    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " required");
        return value;
    }
    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(error);
        return future;
    }
}
