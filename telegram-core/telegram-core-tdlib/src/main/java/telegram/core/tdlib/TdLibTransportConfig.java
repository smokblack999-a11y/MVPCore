package telegram.core.tdlib;

/** Immutable configuration required by the TDLib transport. Keep secrets out of source control. */
public final class TdLibTransportConfig {
    public static final long DEFAULT_REQUEST_TIMEOUT_MS = 60_000L;
    public static final long DEFAULT_CLOSE_TIMEOUT_MS = 15_000L;

    public final int apiId;
    public final String apiHash;
    public final String databaseDirectory;
    public final String filesDirectory;
    public final byte[] databaseEncryptionKey;
    public final String systemLanguageCode;
    public final String deviceModel;
    public final String systemVersion;
    public final String applicationVersion;
    public final long requestTimeoutMs;
    public final long closeTimeoutMs;

    public TdLibTransportConfig(int apiId, String apiHash,
                                String databaseDirectory, String filesDirectory,
                                byte[] databaseEncryptionKey,
                                String systemLanguageCode,
                                String deviceModel,
                                String systemVersion,
                                String applicationVersion) {
        this(apiId, apiHash, databaseDirectory, filesDirectory, databaseEncryptionKey,
                systemLanguageCode, deviceModel, systemVersion, applicationVersion,
                DEFAULT_REQUEST_TIMEOUT_MS, DEFAULT_CLOSE_TIMEOUT_MS);
    }

    public TdLibTransportConfig(int apiId, String apiHash,
                                String databaseDirectory, String filesDirectory,
                                byte[] databaseEncryptionKey,
                                String systemLanguageCode,
                                String deviceModel,
                                String systemVersion,
                                String applicationVersion,
                                long requestTimeoutMs,
                                long closeTimeoutMs) {
        if (apiId <= 0) throw new IllegalArgumentException("apiId must be positive");
        if (apiHash == null || apiHash.trim().isEmpty()) throw new IllegalArgumentException("apiHash required");
        if (databaseDirectory == null || databaseDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("databaseDirectory required");
        }
        if (filesDirectory == null || filesDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("filesDirectory required");
        }
        if (databaseEncryptionKey == null || databaseEncryptionKey.length != 32) {
            throw new IllegalArgumentException("databaseEncryptionKey must contain exactly 32 bytes");
        }
        if (requestTimeoutMs < 1_000L) throw new IllegalArgumentException("requestTimeoutMs must be >= 1000");
        if (closeTimeoutMs < 1_000L) throw new IllegalArgumentException("closeTimeoutMs must be >= 1000");

        this.apiId = apiId;
        this.apiHash = apiHash;
        this.databaseDirectory = databaseDirectory;
        this.filesDirectory = filesDirectory;
        this.databaseEncryptionKey = databaseEncryptionKey.clone();
        this.systemLanguageCode = textOrDefault(systemLanguageCode, "en");
        this.deviceModel = textOrDefault(deviceModel, "Samurai Telegram Client");
        this.systemVersion = textOrDefault(systemVersion, "unknown");
        this.applicationVersion = textOrDefault(applicationVersion, "0.1.0");
        this.requestTimeoutMs = requestTimeoutMs;
        this.closeTimeoutMs = closeTimeoutMs;
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
