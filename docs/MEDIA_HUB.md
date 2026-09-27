# Media Hub

The Android sample now includes a device-side media flow:

1. Camera capture through `ACTION_IMAGE_CAPTURE`.
2. Gallery selection through `ACTION_OPEN_DOCUMENT`.
3. Fine/coarse location permission and a GPS/network location snapshot.
4. Coordinates shown with accuracy in meters.
5. Best-effort GPS EXIF embedding for newly captured JPEGs.
6. Secure `FileProvider` URI sharing.
7. Direct Telegram sharing with a chooser fallback.
8. A visible Media Hub entry point in the sample app.

The feature intentionally uses Android framework APIs so it does not add a new third-party runtime dependency.

## Important limitation

A location fix is device-dependent. `ACCESS_FINE_LOCATION` requests precise permission, but the actual accuracy depends on GPS/network conditions and user settings. The UI reports the reported accuracy instead of pretending coordinates are exact.
