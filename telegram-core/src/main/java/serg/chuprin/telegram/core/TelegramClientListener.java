package serg.chuprin.telegram.core;
public interface TelegramClientListener {
    void onAuthStateChanged(TelegramAuthState state);
    void onChat(TelegramChat chat);
    void onMessage(TelegramMessage message);
    void onError(TelegramClientException error);
}
