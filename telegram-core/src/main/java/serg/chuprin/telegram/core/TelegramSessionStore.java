package serg.chuprin.telegram.core;
public interface TelegramSessionStore {
    String loadDatabaseDirectory();
    void clear() throws TelegramClientException;
}
