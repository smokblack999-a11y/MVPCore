package serg.chuprin.sample.media;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;

public final class LocationReader {
    private static final long SINGLE_UPDATE_TIMEOUT_MS = 12000L;

    private final Context context;
    private final LocationManager manager;

    public LocationReader(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public LocationSnapshot lastKnown() {
        if (!hasLocationPermission()) return null;
        Location best = null;
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
        for (String provider : providers) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            } catch (SecurityException ignored) { }
        }
        return toSnapshot(best);
    }

    public void requestSingle(final Callback callback) {
        if (!hasLocationPermission()) {
            callback.onLocation(null);
            return;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final LocationListener listener = new LocationListener() {
            private boolean delivered;

            private void deliver(Location location) {
                if (delivered) return;
                delivered = true;
                try { manager.removeUpdates(this); } catch (SecurityException ignored) { }
                callback.onLocation(toSnapshot(location));
            }

            @Override public void onLocationChanged(Location location) { deliver(location); }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };

        try {
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper());
            handler.postDelayed(() -> {
                try { manager.removeUpdates(listener); } catch (SecurityException ignored) { }
                LocationSnapshot fallback = lastKnown();
                callback.onLocation(fallback);
            }, SINGLE_UPDATE_TIMEOUT_MS);
        } catch (Exception e) {
            callback.onLocation(lastKnown());
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private LocationSnapshot toSnapshot(Location l) {
        return l == null ? null : new LocationSnapshot(
                l.getLatitude(),
                l.getLongitude(),
                l.hasAccuracy() ? l.getAccuracy() : Float.NaN,
                l.getTime());
    }

    public interface Callback { void onLocation(LocationSnapshot snapshot); }
}
