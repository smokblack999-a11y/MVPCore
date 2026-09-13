package telegram.core.api;

import telegram.core.internal.DefaultTelegramClient;

/** Composition root. A TDLib adapter is injected; the host never depends on it directly. */
public final class TelegramFactory {
    private TelegramFactory() { }

    public static TelegramClient create(TelegramTransport transport) {
        return new DefaultTelegramClient(transport);
    }
}
