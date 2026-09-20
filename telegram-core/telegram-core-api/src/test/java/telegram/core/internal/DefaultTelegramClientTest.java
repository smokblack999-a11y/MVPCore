package telegram.core.internal;

import org.junit.jupiter.api.Test;
import telegram.core.api.AuthorizationState;
import telegram.core.api.Chat;
import telegram.core.api.MediaSpec;
import telegram.core.api.Message;
import telegram.core.api.Page;
import telegram.core.api.SendOptions;
import telegram.core.api.TelegramError;
import telegram.core.api.TransferProgress;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class DefaultTelegramClientTest {
    @Test
    void exposesTransportStateAndForwardsEvents() {
        FakeTransport transport = new FakeTransport();
        DefaultTelegramClient client = new DefaultTelegramClient(transport);
        AtomicReference<AuthorizationState> observed = new AtomicReference<AuthorizationState>();
        client.addListener(new telegram.core.api.TelegramClient.EventListener() {
            public void onAuthStateChanged(AuthorizationState state) { observed.set(state); }
            public void onMessage(Message message) { }
            public void onTransferProgress(TransferProgress progress) { }
            public void onError(TelegramError error) { }
        });

        AuthorizationState ready = new AuthorizationState(AuthorizationState.Type.READY, "test");
        transport.emitState(ready);

        assertTrue(client.getAuthState().join().isReady());
        assertSame(ready, observed.get());
    }

    @Test
    void rejectsInvalidChatBeforeTransportCall() {
        FakeTransport transport = new FakeTransport();
        DefaultTelegramClient client = new DefaultTelegramClient(transport);
        assertThrows(Exception.class, () -> client.getMessages(0, 20, 0).join());
        assertEquals(0, transport.messageCalls);
    }

    private static final class FakeTransport implements TelegramTransport {
        private Listener listener;
        private AuthorizationState state = new AuthorizationState(AuthorizationState.Type.UNKNOWN);
        private int messageCalls;

        public CompletableFuture<AuthorizationState> authorizationState() { return CompletableFuture.completedFuture(state); }
        public CompletableFuture<Void> setPhoneNumber(String value) { return ok(); }
        public CompletableFuture<Void> setEmailAddress(String value) { return ok(); }
        public CompletableFuture<Void> setEmailCode(String value) { return ok(); }
        public CompletableFuture<Void> setCode(String value) { return ok(); }
        public CompletableFuture<Void> resendCode() { return ok(); }
        public CompletableFuture<Void> setPassword(String value) { return ok(); }
        public CompletableFuture<Void> register(String firstName, String lastName) { return ok(); }
        public CompletableFuture<Void> logout() { return ok(); }
        public CompletableFuture<Void> close() { return ok(); }
        public CompletableFuture<Page<Chat>> chats(int limit, long cursor) { return CompletableFuture.completedFuture(new Page<Chat>(null, 0, false)); }
        public CompletableFuture<Page<Message>> messages(long chatId, int limit, long fromMessageId) {
            messageCalls++;
            return CompletableFuture.completedFuture(new Page<Message>(null, 0, false));
        }
        public CompletableFuture<Message> sendText(long chatId, String text, SendOptions options) { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Message> sendMedia(long chatId, MediaSpec media, SendOptions options) { return CompletableFuture.completedFuture(null); }
        public void setListener(Listener listener) { this.listener = listener; }
        void emitState(AuthorizationState value) { state = value; listener.onAuthorizationState(value); }
        private static CompletableFuture<Void> ok() { return CompletableFuture.completedFuture(null); }
    }
}
