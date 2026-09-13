package telegram.core.api;

/** Secure-session boundary. Implementations must store data in protected device storage. */
public interface SessionStore {
    byte[] load(String accountKey);
    void save(String accountKey, byte[] sessionData);
    void clear(String accountKey);
}
