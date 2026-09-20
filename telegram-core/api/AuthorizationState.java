package telegram.core.api;

/** Stable, transport-neutral authorization states exposed to host applications. */
public final class AuthorizationState {
    public enum Type {
        UNKNOWN,
        WAIT_TDLIB_PARAMETERS,
        WAIT_ENCRYPTION_KEY,
        WAIT_PHONE,
        WAIT_PREMIUM_PURCHASE,
        WAIT_EMAIL,
        WAIT_EMAIL_CODE,
        WAIT_CODE,
        WAIT_OTHER_DEVICE_CONFIRMATION,
        WAIT_PASSWORD,
        WAIT_REGISTRATION,
        READY,
        LOGGING_OUT,
        CLOSING,
        CLOSED
    }

    public final Type type;
    public final String detail;

    public AuthorizationState(Type type) { this(type, null); }

    public AuthorizationState(Type type, String detail) {
        if (type == null) throw new IllegalArgumentException("type required");
        this.type = type;
        this.detail = detail;
    }

    public boolean isReady() { return type == Type.READY; }
}