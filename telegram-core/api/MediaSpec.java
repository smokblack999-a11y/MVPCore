package telegram.core.api;

/** Host-provided media descriptor. The core never owns camera/gallery implementations. */
public final class MediaSpec {
    public final String path;
    public final String mimeType;
    public final String fileName;
    public final long sizeBytes;

    public MediaSpec(String path, String mimeType, String fileName, long sizeBytes) {
        if (path == null || path.trim().isEmpty()) throw new IllegalArgumentException("path required");
        this.path = path;
        this.mimeType = mimeType == null ? "application/octet-stream" : mimeType;
        this.fileName = fileName == null ? "file" : fileName;
        this.sizeBytes = Math.max(0L, sizeBytes);
    }
}
