# TDLib integration contract

Telegram Core uses TDLib as the production user-account transport. It does not use the Bot API.

## Why TDLib

TDLib is Telegram's official cross-platform client library. It handles networking, local storage, encryption, ordered updates and asynchronous requests. Its Java interface is generated from Telegram's API schema and uses JNI on platforms such as Android.

## Integration boundary

`TelegramFactory -> DefaultTelegramClient -> TelegramTransport -> TdLibTransport -> TDLib JNI -> Telegram`

Only `TdLibTransport` may import TDLib classes. The public `telegram.core.api` package must remain TDLib-free.

## Initialization

The transport must pass TDLib:

- API ID
- API hash
- writable database directory
- database encryption key
- system language code
- device model
- application version
- secret-chat setting

The API ID/hash belong to the application and must never be committed as source credentials.

## Authorization

Map TDLib authorization updates to the stable states:

- `authorizationStateWaitPhoneNumber` -> `WAIT_PHONE`
- `authorizationStateWaitCode` -> `WAIT_CODE`
- `authorizationStateWaitPassword` -> `WAIT_PASSWORD`
- `authorizationStateReady` -> `READY`
- closing/closed states -> `CLOSING` / `CLOSED`

TDLib can expose additional authorization states. They must be handled explicitly rather than silently treated as READY.

## Messages and media

The adapter maps TDLib objects into the small public models. Local camera/gallery files enter through `MediaSpec`; camera, GPS, EXIF and Android UI remain outside this module.

## Non-negotiable rules

1. Never expose a TDLib class in `telegram.core.api`.
2. Never store API hash, phone number, login code or password in Git.
3. Never log authentication secrets or raw authorization payloads.
4. Never claim a chat/message page is complete when TDLib returned only a partial page.
5. Handle TDLib updates continuously; request/response handling alone is insufficient.
6. Build and test native libraries for the Android ABIs actually supported by the host application.

Official references:
- https://core.telegram.org/tdlib
- https://core.telegram.org/tdlib/getting-started
- https://core.telegram.org/tdlib/Java_API
- https://github.com/tdlib/td/tree/master/example/android
