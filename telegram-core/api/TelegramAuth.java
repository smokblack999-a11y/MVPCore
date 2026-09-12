package telegram.core.api;

import java.util.concurrent.CompletableFuture;

/** Authentication contract for a real Telegram user account. */
public interface TelegramAuth {
    CompletableFuture<TelegramClient.AuthState> state();
    CompletableFuture<Void> submitPhoneNumber(String phoneNumber);
    CompletableFuture<Void> submitCode(String code);
    CompletableFuture<Void> submitPassword(String password);
}
