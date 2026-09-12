package telegram.core.api;

public final class TelegramError {
    public enum Code {
        NETWORK,
        AUTH_REQUIRED,
        AUTH_FAILED,
        RATE_LIMITED,
        PERMISSION_DENIED,
        MEDIA_FAILED,
        UNKNOWN
    }

    public final Code code;
    public final String message;

    public TelegramError(Code code, String message) {
        this.code = code;
        this.message = message;
    }
}
