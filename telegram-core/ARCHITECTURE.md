# Telegram Core Architecture

## Boundary

The core owns Telegram account state, transport, synchronization, chats, messages and media transfer.

The host owns UI, camera, gallery, GPS and product-specific workflows.

## Data flow

Host -> TelegramClient -> transport adapter -> Telegram network

Telegram network -> transport adapter -> TelegramClient.EventListener -> host

## Rules

1. No Bot API assumptions.
2. No Telegram session files in source control.
3. No camera/GPS dependencies in the core.
4. Host code talks only to the public API package.
5. Transport implementation is replaceable behind the API boundary.
6. Authentication state is explicit so the host can render the correct login screen.
7. Media operations expose progress and failures rather than blocking UI threads.
