package telegram.core.tdlib;

import telegram.core.api.TelegramClient;
import telegram.core.api.TelegramFactory;

/** Composition root for the official TDLib JSON transport. */
public final class TdLibClientFactory {
    private TdLibClientFactory() { }

    public static TelegramClient create(TdLibTransportConfig config) {
        return TelegramFactory.create(new TdLibTransport(config));
    }
}
