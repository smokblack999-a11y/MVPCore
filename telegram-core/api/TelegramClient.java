package telegram.core.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Public boundary used by host applications. Transport details stay internal. */
public interface TelegramClient {
    CompletableFuture<AuthState> getAuthState();
    CompletableFuture<AuthState> login();
    CompletableFuture<Void> logout();
    CompletableFuture<List<Chat>> getChats(int limit);
    CompletableFuture<List<Message>> getMessages(long chatId, int limit);
    CompletableFuture<Message> sendText(long chatId, String text);
    CompletableFuture<Message> sendPhoto(long chatId, String filePath, String caption);
    void addListener(EventListener listener);
    void removeListener(EventListener listener);

    interface EventListener {
        void onAuthStateChanged(AuthState state);
        void onMessage(Message message);
        void onError(Throwable error);
    }

    final class AuthState {
        public enum Type { UNKNOWN, WAIT_PHONE, WAIT_CODE, WAIT_PASSWORD, READY, LOGGING_OUT, CLOSED }
        public final Type type;
        public AuthState(Type type) { this.type = type; }
    }

    final class Chat {
        public final long id;
        public final String title;
        public Chat(long id, String title) { this.id = id; this.title = title; }
    }

    final class Message {
        public final long id;
        public final long chatId;
        public final String text;
        public Message(long id, long chatId, String text) {
            this.id = id; this.chatId = chatId; this.text = text;
        }
    }
}
