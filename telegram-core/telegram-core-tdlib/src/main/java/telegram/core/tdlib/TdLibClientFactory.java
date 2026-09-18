package telegram.core.tdlib;

import telegram.core.api.TelegramClient;
import telegram.core.internal.DefaultTelegramClient;
import telegram.core.internal.TelegramTransport;

/** Composition root for the official TDLib JSON transport. */
public final class TdLibClientFactory {
    private TdLibClientFactory() { }

    public static TelegramClient create(TdLibTransportConfig config) {
        TelegramTransport transport = new TdLibTransport(config);
        return new DefaultTelegramClient(transport);
    }
}
