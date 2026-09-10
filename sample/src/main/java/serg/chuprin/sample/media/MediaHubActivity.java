package serg.chuprin.sample.media;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MediaHubActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 41;
    private static final int REQ_LOCATION = 42;
    private static final int REQ_GALLERY = 43;
    private static final int TAKE_PHOTO = 51;
    private static final int PICK_PHOTO = 52;

    private TextView status;
    private Uri pendingPhoto;
    private LocationSnapshot location;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_media_hub);
        status = (TextView) findViewById(R.id.mediaStatus);
        Button camera = (Button) findViewById(R.id.takePhoto);
        Button gallery = (Button) findViewById(R.id.openGallery);
        Button gps = (Button) findViewById(R.id.refreshGps);
        Button telegram = (Button) findViewById(R.id.sendTelegram);
        camera.setOnClickListener(v -> takePhoto());
        gallery.setOnClickListener(v -> pickPhoto());
        gps.setOnClickListener(v -> refreshLocation());
        telegram.setOnClickListener(v -> shareCurrent());
        refreshLocation();
    }

    private void takePhoto() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        refreshLocation();
        try {
            File dir = new File(getCacheDir(), "media");
            if (!dir.exists()) dir.mkdirs();
            String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            File file = new File(dir, name);
            pendingPhoto = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhoto);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, TAKE_PHOTO);
        } catch (Exception e) { status.setText("Camera error: " + e.getMessage()); }
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_PHOTO);
    }

    private void refreshLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        LocationReader reader = new LocationReader(this);
        location = reader.lastKnown();
        status.setText(location == null ? "GPS: waiting for fix" : "GPS: " + location.format());
        reader.requestSingle(snapshot -> runOnUiThread(() -> {
            if (snapshot != null) location = snapshot;
            status.setText(location == null ? "GPS: no fix" : "GPS: " + location.format());
        }));
    }

    private void shareCurrent() {
        if (pendingPhoto != null) TelegramShare.sharePhoto(this, pendingPhoto, location);
        else status.setText("Take or select a photo first");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;
        if (requestCode == PICK_PHOTO && data != null) pendingPhoto = data.getData();
        if (requestCode == TAKE_PHOTO && pendingPhoto != null) {
            String path = new File(getCacheDir(), "media").getAbsolutePath() + "/" + pendingPhoto.getLastPathSegment();
            // FileProvider URI does not expose the filename reliably; EXIF is best-effort via direct cache scan.
            File dir = new File(getCacheDir(), "media");
            File[] files = dir.listFiles();
            if (files != null && files.length > 0) {
                File newest = files[0];
                for (File f : files) if (f.lastModified() > newest.lastModified()) newest = f;
                ExifLocationWriter.write(newest.getAbsolutePath(), location);
            }
        }
        status.setText(location == null ? "Photo ready" : "Photo ready • GPS: " + location.format());
    }
}
