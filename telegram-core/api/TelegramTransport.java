package telegram.core.api;

import java.util.concurrent.CompletableFuture;

/**
 * Internal transport seam. The production Android implementation will adapt TDLib here.
 * No TDLib type is allowed to cross this boundary.
 */
public interface TelegramTransport {
    CompletableFuture<AuthorizationState> authorizationState();
    CompletableFuture<Void> setPhoneNumber(String phoneNumber);
    CompletableFuture<Void> setCode(String code);
    CompletableFuture<Void> setPassword(String password);
    CompletableFuture<Void> close();
    CompletableFuture<Page<Chat>> chats(int limit, long cursor);
    CompletableFuture<Page<Message>> messages(long chatId, int limit, long fromMessageId);
    CompletableFuture<Message> sendText(long chatId, String text, SendOptions options);
    CompletableFuture<Message> sendMedia(long chatId, MediaSpec media, SendOptions options);
    void setListener(Listener listener);

    interface Listener {
        void onAuthorizationState(AuthorizationState state);
        void onMessage(Message message);
        void onError(TelegramError error);
    }
}
