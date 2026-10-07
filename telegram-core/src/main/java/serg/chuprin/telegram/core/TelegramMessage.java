package serg.chuprin.telegram.core;
public final class TelegramMessage {
    public final long id; public final long chatId; public final String text; public final long dateSeconds;
    public TelegramMessage(long id, long chatId, String text, long dateSeconds) {
        this.id=id; this.chatId=chatId; this.text=text==null?"":text; this.dateSeconds=dateSeconds;
    }
}
