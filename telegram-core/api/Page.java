package telegram.core.api;

import java.util.Collections;
import java.util.List;

/** Explicit page result; the core never pretends a partial TDLib response is complete. */
public final class Page<T> {
    public final List<T> items;
    public final long nextCursor;
    public final boolean hasMore;

    public Page(List<T> items, long nextCursor, boolean hasMore) {
        this.items = items == null ? Collections.<T>emptyList() : Collections.unmodifiableList(items);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }
}
