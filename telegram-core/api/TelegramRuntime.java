package telegram.core.api;

/** Runtime parameters required by TDLib initialization, kept independent of Android APIs. */
public final class TelegramRuntime {
    public final String databaseDirectory;
    public final byte[] databaseEncryptionKey;
    public final String systemLanguageCode;
    public final String deviceModel;
    public final String applicationVersion;
    public final boolean useSecretChats;

    public TelegramRuntime(String databaseDirectory, byte[] databaseEncryptionKey,
                           String systemLanguageCode, String deviceModel,
                           String applicationVersion, boolean useSecretChats) {
        if (databaseDirectory == null || databaseDirectory.trim().isEmpty())
            throw new IllegalArgumentException("databaseDirectory required");
        if (databaseEncryptionKey == null || databaseEncryptionKey.length == 0)
            throw new IllegalArgumentException("databaseEncryptionKey required");
        this.databaseDirectory = databaseDirectory;
        this.databaseEncryptionKey = databaseEncryptionKey.clone();
        this.systemLanguageCode = systemLanguageCode == null ? "en" : systemLanguageCode;
        this.deviceModel = deviceModel == null ? "Android" : deviceModel;
        this.applicationVersion = applicationVersion == null ? "1.0" : applicationVersion;
        this.useSecretChats = useSecretChats;
    }
}
