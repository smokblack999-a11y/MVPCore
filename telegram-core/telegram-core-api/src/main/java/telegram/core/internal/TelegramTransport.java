package telegram.core.internal;

import telegram.core.api.AuthorizationState;
import telegram.core.api.Chat;
import telegram.core.api.MediaSpec;
import telegram.core.api.Message;
import telegram.core.api.Page;
import telegram.core.api.SendOptions;
import telegram.core.api.TelegramError;
import telegram.core.api.TransferProgress;

import java.util.concurrent.CompletableFuture;

/**
 * Internal transport seam. Host applications must use TelegramClient instead.
 */
public interface TelegramTransport {
    CompletableFuture<AuthorizationState> authorizationState();
    CompletableFuture<Void> setPhoneNumber(String phoneNumber);
    default CompletableFuture<Void> requestQrCodeAuthentication() {
        CompletableFuture<Void> future = new CompletableFuture<Void>();
        future.completeExceptionally(
                new UnsupportedOperationException("QR authentication is not available in this transport"));
        return future;
    }
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
