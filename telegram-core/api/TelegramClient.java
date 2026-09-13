package telegram.core.api;

import java.util.concurrent.CompletableFuture;

/** Stable public boundary. TDLib/transport classes never cross into the host application. */
public interface TelegramClient {
    CompletableFuture<AuthorizationState> getAuthState();
    CompletableFuture<Void> submitPhoneNumber(String phoneNumber);
    CompletableFuture<Void> submitCode(String code);
    CompletableFuture<Void> submitPassword(String password);
    CompletableFuture<Void> logout();
    CompletableFuture<Page<Chat>> getChats(int limit, long cursor);
    CompletableFuture<Page<Message>> getMessages(long chatId, int limit, long fromMessageId);
    CompletableFuture<Message> sendText(long chatId, String text, SendOptions options);
    CompletableFuture<Message> sendMedia(long chatId, MediaSpec media, SendOptions options);
    void addListener(EventListener listener);
    void removeListener(EventListener listener);

    interface EventListener {
        void onAuthStateChanged(AuthorizationState state);
        void onMessage(Message message);
        void onError(TelegramError error);
    }
}
