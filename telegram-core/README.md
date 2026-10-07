# Telegram Core

Standalone Telegram client core intended to be embedded into Android or other applications.

## Goal

This module targets a normal Telegram user account, not Bot API. The host application supplies Telegram API ID/hash and the core owns account authorization, session, chats, messages and media operations.

## Architecture

- TelegramClient: stable application-facing API.
- TelegramAuthState: authorization lifecycle.
- TelegramSessionStore: persistence boundary.
- TelegramChat, TelegramMessage, TelegramMedia: transport-neutral models.
- TelegramClientListener: updates/events boundary.
- TelegramClientException: normalized failures.

The core does not know about Camera, Gallery, MVPCore, Activities or UI.

## Engine

The production adapter is designed around Telegram TDLib. TDLib is Telegram's client library and exposes the client API, including authorization, chats, messages and media. The official Android build uses native JNI, so the stable Java API is separated from the native engine adapter.

Official references: https://github.com/tdlib/td and https://core.telegram.org/tdlib/getting-started

## Security

Never commit api_id, api_hash, phone numbers, auth codes, session databases or encryption keys. Supply them at runtime and store session data in platform secure storage.
