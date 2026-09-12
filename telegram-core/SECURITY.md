# Security Rules

- Never commit Telegram API credentials, phone numbers, login codes or passwords.
- Never persist authentication state in plain project files.
- Keep session storage private to the runtime/device.
- Treat message and media data as private application data.
- Do not log authentication codes, passwords, authorization tokens or session material.
- The host application must receive explicit error states instead of sensitive exception dumps.
