package serg.chuprin.sample.media;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

public final class LocationReader {
    private final Context context;
    private final LocationManager manager;

    public LocationReader(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public LocationSnapshot lastKnown() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Location best = null;
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
        for (String provider : providers) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            } catch (SecurityException ignored) { }
        }
        return best == null ? null : new LocationSnapshot(best.getLatitude(), best.getLongitude(), best.hasAccuracy() ? best.getAccuracy() : Float.NaN, best.getTime());
    }

    public void requestSingle(final Callback callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onLocation(null);
            return;
        }
        try {
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                @Override public void onLocationChanged(Location location) { callback.onLocation(toSnapshot(location)); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
            }, null);
        } catch (Exception e) {
            callback.onLocation(lastKnown());
        }
    }

    private LocationSnapshot toSnapshot(Location l) {
        return l == null ? null : new LocationSnapshot(l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : Float.NaN, l.getTime());
    }

    public interface Callback { void onLocation(LocationSnapshot snapshot); }
}
