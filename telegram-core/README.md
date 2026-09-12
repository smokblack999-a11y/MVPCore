# Telegram Core

Standalone Telegram user-account client module for reuse by Android and other applications.

## Goal

Provide a clean boundary between the host application and Telegram. The host must not depend on Telegram transport internals.

## Planned capabilities

- User-account authentication (not Bot API)
- Persistent session lifecycle
- Chats, groups and channels
- Message history and synchronization
- Text and media sending
- Media upload/download
- Retry and network recovery
- Notifications/events
- Multiple-account-ready architecture

## Architecture

`Host App -> Telegram Core API -> Telegram client/TDLib -> Telegram account`

Camera and gallery are intentionally outside this module. They can be connected later through the public API.

## Security

Authentication secrets and session data must never be committed to Git. Runtime credentials belong in protected local/device storage.

## Status

Scaffold only. Telegram transport integration is the next implementation step.
