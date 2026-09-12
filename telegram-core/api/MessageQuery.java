package telegram.core.api;

public final class MessageQuery {
    public final long chatId;
    public final int limit;
    public final long fromMessageId;

    public MessageQuery(long chatId, int limit, long fromMessageId) {
        this.chatId = chatId;
        this.limit = Math.max(1, Math.min(limit, 100));
        this.fromMessageId = fromMessageId;
    }
}
