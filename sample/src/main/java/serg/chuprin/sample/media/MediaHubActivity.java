package serg.chuprin.sample.media;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MediaHubActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 41;
    private static final int REQ_LOCATION = 42;
    private static final int TAKE_PHOTO = 51;
    private static final int PICK_PHOTO = 52;

    private static final String STATE_PHOTO = "media.photo";
    private static final String STATE_PHOTO_PATH = "media.photo.path";
    private static final String STATE_LAT = "media.lat";
    private static final String STATE_LON = "media.lon";
    private static final String STATE_ACC = "media.acc";
    private static final String STATE_TIME = "media.time";

    private TextView status;
    private Uri pendingPhoto;
    private String pendingPhotoPath;
    private LocationSnapshot location;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_media_hub);
        status = (TextView) findViewById(R.id.mediaStatus);

        if (state != null) restoreState(state);

        Button camera = (Button) findViewById(R.id.takePhoto);
        Button gallery = (Button) findViewById(R.id.openGallery);
        Button gps = (Button) findViewById(R.id.refreshGps);
        Button telegram = (Button) findViewById(R.id.sendTelegram);
        camera.setOnClickListener(v -> takePhoto());
        gallery.setOnClickListener(v -> pickPhoto());
        gps.setOnClickListener(v -> refreshLocation());
        telegram.setOnClickListener(v -> shareCurrent());

        if (location == null) refreshLocation();
        else updateStatus("GPS: " + location.format());
    }

    private void restoreState(Bundle state) {
        pendingPhoto = state.getParcelable(STATE_PHOTO);
        pendingPhotoPath = state.getString(STATE_PHOTO_PATH);
        if (state.containsKey(STATE_LAT) && state.containsKey(STATE_LON)) {
            location = new LocationSnapshot(
                    state.getDouble(STATE_LAT),
                    state.getDouble(STATE_LON),
                    state.getFloat(STATE_ACC),
                    state.getLong(STATE_TIME));
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (pendingPhoto != null) out.putParcelable(STATE_PHOTO, pendingPhoto);
        if (pendingPhotoPath != null) out.putString(STATE_PHOTO_PATH, pendingPhotoPath);
        if (location != null) {
            out.putDouble(STATE_LAT, location.latitude);
            out.putDouble(STATE_LON, location.longitude);
            out.putFloat(STATE_ACC, location.accuracyMeters);
            out.putLong(STATE_TIME, location.timestamp);
        }
        super.onSaveInstanceState(out);
    }

    private void takePhoto() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        refreshLocation();
        try {
            File dir = new File(getCacheDir(), MediaHubContract.CACHE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                updateStatus("Camera error: cannot create media cache");
                return;
            }

            String name = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                    .format(new Date()) + ".jpg";
            File file = new File(dir, name);
            pendingPhotoPath = file.getAbsolutePath();
            pendingPhoto = FileProvider.getUriForFile(
                    this, getPackageName() + MediaHubContract.AUTHORITY_SUFFIX, file);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhoto);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, TAKE_PHOTO);
        } catch (Exception e) {
            pendingPhoto = null;
            pendingPhotoPath = null;
            updateStatus("Camera error: " + e.getMessage());
        }
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(MediaHubContract.IMAGE_TYPE);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_PHOTO);
        } catch (Exception e) {
            updateStatus("Gallery error: " + e.getMessage());
        }
    }

    private void refreshLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOCATION);
            return;
        }

        LocationReader reader = new LocationReader(this);
        location = reader.lastKnown();
        updateStatus(location == null ? "GPS: waiting for fix" : "GPS: " + location.format());
        reader.requestSingle(snapshot -> runOnUiThread(() -> {
            if (snapshot != null) location = snapshot;
            updateStatus(location == null ? "GPS: no fix" : "GPS: " + location.format());
        }));
    }

    private void shareCurrent() {
        if (pendingPhoto != null) {
            TelegramShare.sharePhoto(this, pendingPhoto, location);
        } else {
            updateStatus("Take or select a photo first");
        }
    }

    private void updateStatus(String text) {
        if (status != null) status.setText(text);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_CAMERA) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                updateStatus("Camera permission denied");
            }
        } else if (requestCode == REQ_LOCATION) {
            boolean granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (granted) refreshLocation();
            else updateStatus("Location permission denied");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PHOTO && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            pendingPhoto = data.getData();
            pendingPhotoPath = null;
            try {
                int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (takeFlags != 0) getContentResolver().takePersistableUriPermission(pendingPhoto, takeFlags);
            } catch (Exception ignored) { }
            updateStatus(location == null ? "Gallery photo ready" : "Gallery photo ready • GPS: " + location.format());
            return;
        }

        if (requestCode == TAKE_PHOTO) {
            if (resultCode == Activity.RESULT_OK && pendingPhoto != null) {
                if (pendingPhotoPath != null) {
                    File photoFile = new File(pendingPhotoPath);
                    if (photoFile.isFile() && photoFile.length() > 0L) {
                        ExifLocationWriter.write(photoFile.getAbsolutePath(), location);
                    }
                }
                updateStatus(location == null ? "Photo ready" : "Photo ready • GPS: " + location.format());
            } else {
                updateStatus("Photo capture cancelled");
            }
        }
    }
}
