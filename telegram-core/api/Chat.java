package telegram.core.api;

/** Immutable chat model safe for host UI layers. */
public final class Chat {
    public final long id;
    public final String title;
    public final long lastMessageId;

    public Chat(long id, String title, long lastMessageId) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.lastMessageId = lastMessageId;
    }
}
