# Telegram Core — Product Design

## Product definition

Telegram Core is a reusable Android-first Telegram **user-account client module**. It is not a Bot API wrapper and it does not own camera, gallery, GPS or EXIF.

The host application integrates through the stable `telegram.core.api` package.

## Core user journey

```
create client
  -> WAIT_TDLIB_PARAMETERS
  -> WAIT_PHONE
  -> submitPhoneNumber
  -> WAIT_CODE
  -> submitCode
  -> [WAIT_PASSWORD / WAIT_EMAIL / other supported auth states]
  -> READY
  -> getChats
  -> getMessages
  -> sendText
  -> sendMedia(photo)
  -> sendMedia(video)
```

An already authorized account must be able to restart the process and return to READY from its encrypted local TDLib database without asking for the phone again.

## Module boundaries

### Public API

- authorization state machine
- chat/message models
- pagination
- text sending
- media sending
- progress events
- stable error taxonomy
- lifecycle controls

### Transport

- TDLib JSON/JNI only
- all generated TDLib classes stay internal
- request/response correlation through `@extra`
- continuous update processing
- exact TDLib schema pinned by commit

### Android adapter

- app-private TDLib database/files directories
- Android Keystore protection for the database key
- runtime-only API ID/API hash
- device/language/application metadata

### Host application

- camera
- gallery/document picker
- GPS
- EXIF
- UI/navigation
- product-specific workflows

## Quality gates

### Build gate

A clean checkout must:

1. verify the exact TDLib commit and toolchain lock;
2. generate the official JSONJava binding;
3. build `libtdjsonjava.so` for arm64-v8a, armeabi-v7a, x86_64 and x86;
4. package the AAR;
5. build the acceptance APK;
6. pass Android JNI runtime tests.

### Protocol gate

The transport must:

- never map unknown auth states to READY;
- preserve TDLib update order;
- maintain main-chat ordering from chat position updates;
- keep message pagination cursor-based;
- reconcile successful and failed message-send updates;
- distinguish upload progress from download progress;
- keep credentials and session material out of Git and logs.

### Real-device gate

Production status requires a physical Android device to prove:

`WAIT_PHONE -> WAIT_CODE -> READY -> chats -> text -> photo -> video`

and to prove persistence after process restart.

## Product packaging

The reusable product should be distributed as:

- `telegram-core-api`: stable host-facing contracts;
- `telegram-core-tdlib`: transport implementation;
- `telegram-core-tdlib-android`: Android/native package;
- acceptance sample: integration reference, not a production UI.

This separation allows the Telegram component to be reused by MVPCore, SamuraiOS or another host without importing camera/GPS implementation details.

## Deliberately not promised by the first release

The first release does not claim complete Telegram feature parity. Notifications/background service, message search, albums, advanced media controls, proxy UI, full secret-chat UI and other specialized Telegram surfaces are separate milestones.

The release gate is feature-specific and evidence-based: only features backed by passing tests and real-device acceptance are marked production-ready.
