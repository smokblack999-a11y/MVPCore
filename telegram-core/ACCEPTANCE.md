# Telegram Core acceptance gate

This document defines the first real-device milestone. The component is a Telegram **user-account client**, not a Bot API integration.

## Required secrets

Create an application at Telegram's official API developer page and provide the resulting api_id and api_hash only at runtime. Never commit them to Git.

The acceptance APK intentionally has visible input fields for these values because it is a manual test harness, not a production credential store.

## Expected authentication flow

1. Start the acceptance APK and press CONNECT.
2. The client must report WAIT_TDLIB_PARAMETERS briefly while TDLib initializes.
3. The client must then reach WAIT_PHONE.
4. Press SUBMIT PHONE with the account's international phone number.
5. The client must reach WAIT_CODE.
6. Enter the code received from Telegram and press SUBMIT CODE.
7. If the account has 2FA, the client must reach WAIT_PASSWORD; enter the password and submit it.
8. The successful state is READY.
9. Press LOAD CHATS. A real chat list must appear.
10. Select a chat and send a text message.
11. Pick a photo and a video from Android storage and send each.
12. Transfer progress must be observable through the event listener.

## Persistence gate

The Android factory stores the TDLib database under the application's noBackupFilesDir. The database encryption key is generated randomly and protected by Android Keystore. Restarting the app must reuse the same account database so an already authorized account can return to READY without entering the phone/code again.

## Native gate

CI must build the exact TDLib commit in TDLIB_VERSION, generate the official JsonClient.java, produce non-empty libtdjsonjava.so for all four supported ABIs, package them into the AAR, and execute the JNI smoke tests on an Android emulator.

## Out of scope for this gate

Notifications, background synchronization, message search, downloads, albums, secret-chat UI, proxy UI, and complete Telegram feature parity are later milestones. No fake success path is accepted.
