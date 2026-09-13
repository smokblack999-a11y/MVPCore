package telegram.core.tdlib;

import telegram.core.api.TelegramConfig;
import telegram.core.api.TelegramFactory;

/** Creates independent Telegram user-account clients for host applications. */
public final class TdTelegramFactory implements TelegramFactory {
    @Override
    public TdTelegramClient create(TelegramConfig config, SessionStore sessionStore) {
        if (config == null) throw new IllegalArgumentException("config required");
        if (sessionStore == null) throw new IllegalArgumentException("sessionStore required");
        return new TdTelegramClient(config, sessionStore);
    }
}
