package telegram.core.api;

import java.util.concurrent.CompletableFuture;

/** Internal transport seam. The production Android implementation adapts TDLib here. */
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
        void onTransferProgress(TransferProgress progress);
        void onError(TelegramError error);
    }
}
