package telegram.core.api;

public interface TelegramFactory {
    TelegramClient create(TelegramConfig config, SessionStore sessionStore);

    interface SessionStore {
        byte[] load();
        void save(byte[] session);
        void clear();
    }
}
