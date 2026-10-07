package serg.chuprin.telegram.core;
public final class TelegramClientConfig {
    public final int apiId; public final String apiHash; public final String databaseDirectory;
    public TelegramClientConfig(int apiId, String apiHash, String databaseDirectory) {
        if (apiId <= 0) throw new IllegalArgumentException("apiId must be positive");
        if (apiHash == null || apiHash.isEmpty()) throw new IllegalArgumentException("apiHash is empty");
        if (databaseDirectory == null || databaseDirectory.isEmpty()) throw new IllegalArgumentException("databaseDirectory is empty");
        this.apiId=apiId; this.apiHash=apiHash; this.databaseDirectory=databaseDirectory;
    }
}
