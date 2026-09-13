package telegram.core.api;

public final class TelegramConfig {
    public final int apiId;
    public final String apiHash;
    public final String databaseDirectory;
    public final String filesDirectory;
    public final String deviceModel;
    public final String applicationVersion;

    public TelegramConfig(int apiId, String apiHash, String databaseDirectory, String filesDirectory,
                          String deviceModel, String applicationVersion) {
        if (apiId <= 0) throw new IllegalArgumentException("apiId must be positive");
        if (apiHash == null || apiHash.trim().isEmpty()) throw new IllegalArgumentException("apiHash required");
        if (databaseDirectory == null || databaseDirectory.trim().isEmpty()) throw new IllegalArgumentException("databaseDirectory required");
        if (filesDirectory == null || filesDirectory.trim().isEmpty()) throw new IllegalArgumentException("filesDirectory required");
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.databaseDirectory = databaseDirectory;
        this.filesDirectory = filesDirectory;
        this.deviceModel = deviceModel == null || deviceModel.trim().isEmpty() ? "Samurai Telegram Core" : deviceModel;
        this.applicationVersion = applicationVersion == null || applicationVersion.trim().isEmpty() ? "0.1.0" : applicationVersion;
    }
}
