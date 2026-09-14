package telegram.core.api;

import java.util.concurrent.CompletableFuture;

/** Internal transport seam. The production implementation adapts the official TDLib client. */
public interface TelegramTransport {
    CompletableFuture<AuthorizationState> authorizationState();
    CompletableFuture<Void> setPhoneNumber(String phoneNumber);
    CompletableFuture<Void> requestQrCodeAuthentication();
    CompletableFuture<Void> setEmailAddress(String emailAddress);
    CompletableFuture<Void> setEmailCode(String code);
    CompletableFuture<Void> setCode(String code);
    CompletableFuture<Void> resendCode();
    CompletableFuture<Void> setPassword(String password);
    CompletableFuture<Void> register(String firstName, String lastName);
    CompletableFuture<Void> logout();
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
