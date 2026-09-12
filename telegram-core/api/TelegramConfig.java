package telegram.core.api;

public final class TelegramConfig {
    public final int apiId;
    public final String apiHash;

    public TelegramConfig(int apiId, String apiHash) {
        if (apiId <= 0) throw new IllegalArgumentException("apiId must be positive");
        if (apiHash == null || apiHash.trim().isEmpty()) throw new IllegalArgumentException("apiHash required");
        this.apiId = apiId;
        this.apiHash = apiHash;
    }
}
