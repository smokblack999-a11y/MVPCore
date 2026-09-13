package telegram.core.tdlib;

/** Configuration required by the TDLib transport. Keep secrets out of source control. */
public final class TdLibTransportConfig {
    public final int apiId;
    public final String apiHash;
    public final String databaseDirectory;
    public final String filesDirectory;
    public final byte[] databaseEncryptionKey;
    public final String systemLanguageCode;
    public final String deviceModel;
    public final String systemVersion;
    public final String applicationVersion;

    public TdLibTransportConfig(int apiId, String apiHash,
                                String databaseDirectory, String filesDirectory,
                                byte[] databaseEncryptionKey,
                                String systemLanguageCode,
                                String deviceModel,
                                String systemVersion,
                                String applicationVersion) {
        if (apiId <= 0) throw new IllegalArgumentException("apiId must be positive");
        if (apiHash == null || apiHash.trim().isEmpty()) throw new IllegalArgumentException("apiHash required");
        if (databaseDirectory == null || databaseDirectory.trim().isEmpty()) throw new IllegalArgumentException("databaseDirectory required");
        if (filesDirectory == null || filesDirectory.trim().isEmpty()) throw new IllegalArgumentException("filesDirectory required");
        if (databaseEncryptionKey == null || databaseEncryptionKey.length != 32) {
            throw new IllegalArgumentException("databaseEncryptionKey must contain exactly 32 bytes");
        }
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.databaseDirectory = databaseDirectory;
        this.filesDirectory = filesDirectory;
        this.databaseEncryptionKey = databaseEncryptionKey.clone();
        this.systemLanguageCode = languageOrDefault(systemLanguageCode, "en");
        this.deviceModel = languageOrDefault(deviceModel, "Samurai Telegram Client");
        this.systemVersion = languageOrDefault(systemVersion, "unknown");
        this.applicationVersion = languageOrDefault(applicationVersion, "0.1.0");
    }

    private static String languageOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
