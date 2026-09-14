# Reproducible TDLib Android native build

The production transport uses TDLib's official JSON-over-JNI interface (`JSONJava`). The native build is intentionally pinned instead of consuming an unpinned Maven binary.

Pinned source and toolchain are in `TDLIB_LOCK`.

The official TDLib Android build produces:

- `JsonClient.java`
- `libtdjsonjava.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`

The build is performed in CI from the exact TDLib commit using TDLib's own Android Docker build instructions. No Telegram session, API hash, phone number, code, or password is stored in this directory.

Do not replace the pinned source with a moving branch in production. Updating TDLib is a deliberate dependency upgrade: change `TDLIB_LOCK`, run the native verification workflow, inspect the generated API surface, then promote the new artifact.
