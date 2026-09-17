# TDLib reproducibility lock

This module is intentionally pinned to one exact upstream TDLib source revision.

- TDLib release: `v1.8.66`
- TDLib source commit: `022d60202e446ad1287b9fb68e687c8a0760788b`
- Android NDK: `23.2.8568313`
- Android CMake: `3.22.1`
- Android API floor used by the upstream build script: `16` (64-bit OpenSSL builds use API 21)
- Interface: `JSONJava`
- STL: `c++_static`
- OpenSSL source tag: `OpenSSL_1_1_1w`
- Android ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`

The native artifact is `libtdjsonjava.so`. The Java side is the upstream
`org.drinkless.tdlib.JsonClient` generated/provided by the same TDLib revision.

Do not replace these versions casually. A TDLib revision change is a compatibility
change and must regenerate the Java binding and all native ABIs, then pass the
transport contract tests and Android build before being accepted.

Upstream source and build logic are taken from TDLib's official Android example.
