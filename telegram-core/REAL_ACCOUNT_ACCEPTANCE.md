# Real-account acceptance gate

This module is not considered production-ready until a real Android device passes the following sequence with the pinned native artifact:

1. Native `libtdjsonjava.so` loads without `UnsatisfiedLinkError`.
2. TDLib emits `authorizationStateWaitTdlibParameters`; the transport automatically supplies initialization parameters.
3. Fresh session reaches `WAIT_PHONE`.
4. User enters a phone number; the transport reaches `WAIT_CODE`.
5. User enters the Telegram login code; if enabled, the flow reaches `WAIT_PASSWORD`.
6. Correct 2FA password reaches `READY`.
7. Existing session restart returns directly to `READY` without asking for the phone again.
8. Main chat list loads and returns stable chat IDs/titles.
9. Chat history loads in TDLib's documented reverse-chronological order.
10. Text message sends and an outgoing message/update is observed.
11. Local JPEG/PNG sends as a photo and produces transfer progress.
12. Local MP4 sends as a video and produces transfer progress.
13. Process restart preserves the encrypted TDLib database/session.
14. Logout reaches `CLOSED` and the next login starts from the expected authorization state.

## Required credentials

The host application supplies its own Telegram `api_id` and `api_hash` obtained from Telegram. They are runtime configuration, never source-controlled credentials.

The account phone number, authentication code, 2FA password, and database encryption key are runtime secrets. They must never appear in logs, tests, Git history, or CI artifacts.

## Why this gate is strict

TDLib is asynchronous. Authorization is driven by `updateAuthorizationState`, and all updates must be processed in receive order. Chat/message/media functionality is tested only after `READY`; a successful Java compilation is not evidence of a working Telegram client.
