# Public API

The host should depend on this package only.

## Authentication

Use `TelegramAuth` to submit phone number, login code and optional 2FA password. The implementation reports state through `TelegramClient.AuthState`.

## Messaging

Use `TelegramClient.getChats`, `getMessages`, `sendText` and `sendPhoto`.

## Integration rule

Camera/GPS/Gallery adapters belong to the host. They produce media inputs for the Telegram core instead of becoming dependencies of the core itself.
