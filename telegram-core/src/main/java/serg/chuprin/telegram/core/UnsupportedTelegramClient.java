package serg.chuprin.telegram.core;
import java.util.List;
public final class UnsupportedTelegramClient implements TelegramClient {
    private TelegramAuthState state=TelegramAuthState.WAITING_PARAMETERS;
    public UnsupportedTelegramClient(TelegramClientConfig config) { }
    private TelegramClientException unavailable() { return new TelegramClientException("TDLib adapter is not installed"); }
    public void start() throws TelegramClientException { throw unavailable(); }
    public TelegramAuthState getAuthState() { return state; }
    public void setPhoneNumber(String v) throws TelegramClientException { throw unavailable(); }
    public void setAuthenticationCode(String v) throws TelegramClientException { throw unavailable(); }
    public void setPassword(String v) throws TelegramClientException { throw unavailable(); }
    public List<TelegramChat> getChats(int limit) throws TelegramClientException { throw unavailable(); }
    public List<TelegramMessage> getMessages(long chatId,long fromMessageId,int limit) throws TelegramClientException { throw unavailable(); }
    public long sendText(long chatId,String text) throws TelegramClientException { throw unavailable(); }
    public long sendMedia(long chatId,TelegramMedia media,String caption) throws TelegramClientException { throw unavailable(); }
    public void logout() throws TelegramClientException { throw unavailable(); }
    public void addListener(TelegramClientListener listener) { }
    public void removeListener(TelegramClientListener listener) { }
    public void close() { state=TelegramAuthState.CLOSED; }
}
