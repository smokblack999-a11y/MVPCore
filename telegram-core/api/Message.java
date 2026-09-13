package telegram.core.api;

/** Transport-neutral message projection. Content-specific fields can be added without exposing TDLib types. */
public final class Message {
    public final long id;
    public final long chatId;
    public final String text;
    public final long timestampSeconds;

    public Message(long id, long chatId, String text, long timestampSeconds) {
        this.id = id;
        this.chatId = chatId;
        this.text = text == null ? "" : text;
        this.timestampSeconds = timestampSeconds;
    }
}
