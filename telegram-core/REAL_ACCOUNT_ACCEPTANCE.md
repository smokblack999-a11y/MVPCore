# Real-account acceptance gate

This module is not considered production-ready until a real Android device passes the following sequence with the exact pinned native artifact:

1. Native `libtdjsonjava.so` loads without `UnsatisfiedLinkError`.
2. TDLib starts from `authorizationStateWaitTdlibParameters`; the transport supplies initialization parameters using the pinned JSON schema.
3. A fresh session reaches `WAIT_PHONE`.
4. A phone number in international format reaches `WAIT_CODE`.
5. The Telegram login code is accepted; accounts with 2-step verification reach `WAIT_PASSWORD`.
6. The correct 2FA password reaches `READY`.
7. Process restart with the same account ID reuses the same encrypted local database and returns to the correct authorization state without asking for the phone again.
8. The main chat list loads in stable TDLib order.
9. Chat history returns in reverse chronological order; pagination does not duplicate the boundary message.
10. Text sends successfully and the outgoing message/update is observable.
11. A local JPEG/PNG sends through the real `inputMessagePhoto -> inputPhoto -> inputFileLocal` chain and emits upload progress.
12. A local H.264/MPEG-4 MP4 sends through `inputMessageVideo -> inputVideo -> inputFileLocal` and emits upload progress.
13. Android Keystore-backed database key survives process restart and does not depend on exported app storage.
14. Graceful `close()` waits for `authorizationStateClosed` or fails only after the configured close timeout.
15. `logOut` reaches `authorizationStateClosed`; the next client instance for the same account ID starts a new authorization session.

## Runtime setup

Use a real Telegram application `api_id` and `api_hash` obtained for the client. Supply them at runtime; do not commit them.

For Android, construct the client through `AndroidTelegramClientFactory`:

```java
import telegram.core.api.Message;

TelegramClient client = AndroidTelegramClientFactory.create(
    context,
    "personal",
    runtimeApiId,
    runtimeApiHash
);

client.addListener(new TelegramClient.EventListener() {
    @Override public void onAuthStateChanged(AuthorizationState state) {
        // Render WAIT_PHONE / WAIT_CODE / WAIT_PASSWORD / READY.
    }

    @Override public void onMessage(Message message) { }

    @Override public void onTransferProgress(TransferProgress progress) { }

    @Override public void onError(TelegramError error) { }
});
```

Then drive only the public API:

`WAIT_PHONE -> submitPhoneNumber -> WAIT_CODE -> submitCode -> [WAIT_PASSWORD -> submitPassword] -> READY`

After `READY`, verify:

`getChats -> getMessages -> sendText -> sendMedia(photo) -> sendMedia(video)`

## Secrets and evidence

The account phone number, login code, 2FA password and TDLib database encryption key are runtime secrets. Never place them in Git, test fixtures, CI logs, screenshots or artifacts.

The acceptance device should use a disposable test account or another account whose owner explicitly approves the test. Do not automate or store the Telegram login code outside the test UI.

## Why this gate is strict

TDLib is asynchronous and correctness depends on processing updates in receive order. `updateAuthorizationState` controls authentication, `updateNewChat` precedes chat identifiers, and `getChatHistory` can return fewer than the requested limit even when more history exists. A successful Java build is therefore not evidence of a working Telegram client.

**Status: implementation complete enough for physical-device acceptance; real-account gate remains open until the device sequence above is observed.**
