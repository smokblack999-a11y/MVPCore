package telegram.core.api;

/** Stable error taxonomy. Raw transport errors stay behind the public boundary. */
public final class TelegramError extends Exception {
    public enum Code {
        NETWORK,
        AUTH_REQUIRED,
        AUTH_FAILED,
        RATE_LIMITED,
        PERMISSION_DENIED,
        MEDIA_FAILED,
        INVALID_ARGUMENT,
        INTERNAL
    }

    public final Code code;

    public TelegramError(Code code, String message) {
        super(message == null ? code.name() : message);
        if (code == null) throw new IllegalArgumentException("code required");
        this.code = code;
    }
}
