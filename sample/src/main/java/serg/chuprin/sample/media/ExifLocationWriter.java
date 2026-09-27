package serg.chuprin.sample.media;

import android.media.ExifInterface;

public final class ExifLocationWriter {
    private ExifLocationWriter() { }

    public static void write(String path, LocationSnapshot location) {
        if (location == null) return;
        try {
            ExifInterface exif = new ExifInterface(path);
            exif.setLatLong(location.latitude, location.longitude);
            exif.saveAttributes();
        } catch (Exception ignored) { }
    }
}
