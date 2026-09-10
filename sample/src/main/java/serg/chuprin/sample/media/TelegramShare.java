package serg.chuprin.sample.media;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class TelegramShare {
    private TelegramShare() { }

    public static void sharePhoto(Context context, Uri uri, LocationSnapshot location) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        if (location != null) intent.putExtra(Intent.EXTRA_TEXT, "GPS: " + location.format());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("photo", uri));
        intent.setPackage("org.telegram.messenger");
        try {
            context.startActivity(intent);
        } catch (Exception unavailable) {
            intent.setPackage(null);
            context.startActivity(Intent.createChooser(intent, "Share photo"));
        }
    }
}
