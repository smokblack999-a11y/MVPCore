package telegram.core.api;

/** Monotonic media transfer progress exposed without leaking TDLib file objects. */
public final class TransferProgress {
    public final String fileName;
    public final long completedBytes;
    public final long totalBytes;
    public final boolean completed;

    public TransferProgress(String fileName, long completedBytes, long totalBytes, boolean completed) {
        this.fileName = fileName == null ? "file" : fileName;
        this.completedBytes = Math.max(0L, completedBytes);
        this.totalBytes = Math.max(0L, totalBytes);
        this.completed = completed;
    }
}
