package telegram.core.api;

public final class ChatQuery {
    public final int limit;
    public final String search;

    public ChatQuery(int limit, String search) {
        this.limit = Math.max(1, Math.min(limit, 100));
        this.search = search == null ? "" : search;
    }
}
