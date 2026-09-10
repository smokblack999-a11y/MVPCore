package serg.chuprin.sample.media;

public final class LocationSnapshot {
    public final double latitude;
    public final double longitude;
    public final float accuracyMeters;
    public final long timestamp;

    public LocationSnapshot(double latitude, double longitude, float accuracyMeters, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.timestamp = timestamp;
    }

    public String format() {
        return String.format(java.util.Locale.US, "%.7f, %.7f (±%.1fm)", latitude, longitude, accuracyMeters);
    }
}
