package serg.chuprin.telegram.core;
public final class TelegramMedia {
    public enum Type { PHOTO, VIDEO, DOCUMENT }
    public final Type type; public final String path; public final String mimeType;
    public TelegramMedia(Type type, String path, String mimeType) {
        if (type == null) throw new IllegalArgumentException("type == null");
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("path is empty");
        this.type=type; this.path=path; this.mimeType=mimeType==null?"application/octet-stream":mimeType;
    }
}
