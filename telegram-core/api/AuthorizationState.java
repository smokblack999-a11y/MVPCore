package telegram.core.api;

/** Stable, transport-neutral authorization states exposed to host applications. */
public final class AuthorizationState {
    public enum Type {
        UNKNOWN,
        WAIT_PHONE,
        WAIT_CODE,
        WAIT_PASSWORD,
        READY,
        CLOSING,
        CLOSED
    }

    public final Type type;
    public final String detail;

    public AuthorizationState(Type type) {
        this(type, null);
    }

    public AuthorizationState(Type type, String detail) {
        if (type == null) throw new IllegalArgumentException("type required");
        this.type = type;
        this.detail = detail;
    }

    public boolean isReady() {
        return type == Type.READY;
    }
}
