# Reproducible TDLib Android native build

The production transport uses TDLib's official JSON-over-JNI interface (`JSONJava`). The native build is intentionally pinned instead of consuming an unpinned Maven binary.

Pinned source and toolchain are in `../TDLIB_VERSION`. The canonical CI build entry point is `../scripts/build-tdlib-android.sh`.

The official TDLib Android build produces:

- `JsonClient.java`
- `libtdjsonjava.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`

The canonical CI build is performed from the exact TDLib commit using TDLib's own Android build instructions. The optional Docker helper in this directory also receives the pinned TDLib, Android NDK, OpenSSL, STL, and interface values. No Telegram session, API hash, phone number, code, or password is stored in this directory.

Do not replace the pinned source with a moving branch in production. Updating TDLib is a deliberate dependency upgrade: change `../TDLIB_VERSION`, run the native verification workflow, inspect the generated API surface, then promote the new artifact.
