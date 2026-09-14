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

**Implementation: real TDLib adapter present, production integration gate not yet passed.**

The project deliberately does not call the module production-ready until native TDLib and real-account integration tests pass.
