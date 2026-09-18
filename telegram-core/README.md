# Telegram Core

Standalone Telegram user-account client module for reuse by Android and other applications.

## Goal

Provide a clean boundary between the host application and Telegram. The host must not depend on Telegram transport internals.

## Architecture

`Host App -> Telegram Core API -> transport -> TDLib JSON/JNI -> Telegram account`

Camera, gallery, GPS and EXIF are intentionally outside this module. They enter through `MediaSpec` and the public API later.

## What is already locked

- User-account architecture, not Bot API.
- Transport-neutral public API.
- Explicit authorization state model.
- Phone, email, code, password and registration contracts.
- QR authentication contract.
- Chat/message/media abstractions.
- Session/database encryption boundary.
- Deterministic API contract tests.
- TDLib-specific code isolated in the transport module.

## Production gates still required

- Pin and build an exact TDLib revision for Android.
- Ship native libraries for the deliberately supported ABIs.
- Validate every current authorization state against the real TDLib runtime.
- Replace any legacy transport behavior that does not match the pinned TDLib schema.
- Validate chat cache/update ordering, message pagination, media upload and send-result reconciliation.
- Run real-account integration tests and clean CI from a fresh checkout.

## Security

Authentication secrets, API credentials and runtime session/database data must never be committed to Git. Sensitive values must never appear in logs.

## Status

**Architecture: locked.**

**Implementation: real TDLib JSON/JNI adapter present; production integration gate is still open.**

The project deliberately does not call the module production-ready until native TDLib and real-account integration tests pass.

## Current hardening status

- TDLib revision is pinned in `TDLIB_VERSION` and the Android build regenerates JSONJava from that exact revision.
- The generated official `org.drinkless.tdlib.JsonClient` is the only Android JSON binding; no handwritten duplicate is packaged.
- Android CI verifies non-empty `libtdjsonjava.so` for arm64-v8a, armeabi-v7a, x86_64 and x86 and runs a real JNI smoke test on an emulator.
- Authorization covers phone, code, email, password, registration, QR, premium-purchase, other-device-confirmation, READY, logout and close states.
- Logout is separated from transport shutdown.
- Media transfer progress tracks both upload and download completion.
- The host can construct the client through `TdLibClientFactory` without touching generated TDLib classes.

The remaining production gate is deliberately real-world: build the pinned native artifacts from a clean checkout, run the Android emulator smoke test, then perform a disposable real-account login and verify READY -> chats -> text -> photo/video on a physical device. API ID/hash and the account/session never belong in Git.
