package serg.chuprin.telegram.core;
import java.util.List;
public interface TelegramClient extends AutoCloseable {
    void start() throws TelegramClientException;
    TelegramAuthState getAuthState();
    void setPhoneNumber(String phoneNumber) throws TelegramClientException;
    void setAuthenticationCode(String code) throws TelegramClientException;
    void setPassword(String password) throws TelegramClientException;
    List<TelegramChat> getChats(int limit) throws TelegramClientException;
    List<TelegramMessage> getMessages(long chatId, long fromMessageId, int limit) throws TelegramClientException;
    long sendText(long chatId, String text) throws TelegramClientException;
    long sendMedia(long chatId, TelegramMedia media, String caption) throws TelegramClientException;
    void logout() throws TelegramClientException;
    void addListener(TelegramClientListener listener);
    void removeListener(TelegramClientListener listener);
    void close();
}
