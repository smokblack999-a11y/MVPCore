# TDLib integration contract

Telegram Core uses TDLib as the production user-account transport. It does not use the Bot API.

## Why TDLib

TDLib is Telegram's official cross-platform client library. It handles networking, local storage, encryption, ordered updates and asynchronous requests. Its JSON/JNI interface is suitable for keeping the generated/native layer behind one transport boundary.

## Integration boundary

`Host -> telegram.core.api -> DefaultTelegramClient -> TelegramTransport -> TdLibTransport -> TDLib JSON/JNI -> Telegram`

Only `TdLibTransport` may know TDLib JSON details. The public `telegram.core.api` package must remain TDLib-free.

## Initialization

The transport must provide TDLib:

- API ID
- API hash
- writable database directory
- writable files directory
- database encryption key
- system language code
- device model
- system version
- application version
- secret-chat setting

API ID/hash are application configuration, not source credentials. The database encryption key is generated/stored by the host's secure storage adapter and never committed.

## Authorization

The adapter must handle the complete current authorization surface, including:

- `authorizationStateWaitTdlibParameters`
- `authorizationStateWaitPhoneNumber`
- `authorizationStateWaitPremiumPurchase`
- `authorizationStateWaitEmailAddress`
- `authorizationStateWaitEmailCode`
- `authorizationStateWaitCode`
- `authorizationStateWaitOtherDeviceConfirmation`
- `authorizationStateWaitRegistration`
- `authorizationStateWaitPassword`
- `authorizationStateReady`
- `authorizationStateLoggingOut`
- `authorizationStateClosing`
- `authorizationStateClosed`

Unknown states are mapped to `UNKNOWN`, never to `READY`.

## Chat and message correctness

TDLib is update-driven. `updateNewChat` is guaranteed before a chat identifier is returned, so the production adapter must maintain a chat cache from updates rather than repeatedly treating `getChat` as the source of truth. `loadChats`/`getChats` are used to populate and page the list; the public API must not claim completeness when TDLib returns a partial result.

Message history uses TDLib's reverse-chronological `getChatHistory` semantics. The public cursor is an explicit message identifier, not a guessed page number.

## Media correctness

A local file is represented by `inputFileLocal`. `inputMessagePhoto.photo`, `inputMessageVideo.video`, and `inputMessageDocument.document` all accept the appropriate `InputFile` object in the current TDLib schema. The adapter must supply the complete media object fields required by the pinned schema and must not guess fields from an older TDLib version.

## Native build

TDLib's official Android example builds native libraries and Java/JSON bindings from source. The project pins an exact TDLib source commit and builds reproducibly in CI. The current lock is `telegram-core/native/TDLIB_LOCK`; the current build interface is `JSONJava`, producing `JsonClient.java` plus `libtdjsonjava.so`.

## Non-negotiable rules

1. Never expose a TDLib class in `telegram.core.api`.
2. Never store API hash, phone number, login code or password in Git.
3. Never log authentication secrets or raw authorization payloads.
4. Never treat an unknown authorization state as READY.
5. Never claim a chat/message page is complete when TDLib returned only a partial page.
6. Handle TDLib updates continuously; request/response handling alone is insufficient.
7. Distinguish Telegram `logOut` from local transport shutdown.
8. Do not declare the module production-ready until the native TDLib build and a real-account integration test pass.

Official references:
- https://core.telegram.org/tdlib
- https://core.telegram.org/tdlib/getting-started
- https://core.telegram.org/tdlib/Java_API
- https://github.com/tdlib/td/tree/master/example/android
