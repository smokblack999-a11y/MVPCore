package serg.chuprin.sample.media;

import android.net.Uri;

public final class MediaItem {
    private final Uri uri;
    private final double latitude;
    private final double longitude;
    private final long timestamp;

    public MediaItem(Uri uri, double latitude, double longitude, long timestamp) {
        this.uri = uri;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public Uri getUri() { return uri; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getTimestamp() { return timestamp; }
    public boolean hasLocation() { return !Double.isNaN(latitude) && !Double.isNaN(longitude); }
}
