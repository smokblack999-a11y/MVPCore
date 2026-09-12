package telegram.core.api;

/** Stable event names for adapters such as Android UI, camera and gallery. */
public final class TelegramEvents {
    private TelegramEvents() { }

    public static final String AUTH_STATE_CHANGED = "auth_state_changed";
    public static final String CHAT_UPDATED = "chat_updated";
    public static final String MESSAGE_RECEIVED = "message_received";
    public static final String MESSAGE_SEND_PROGRESS = "message_send_progress";
    public static final String MESSAGE_SEND_COMPLETED = "message_send_completed";
    public static final String MEDIA_DOWNLOAD_PROGRESS = "media_download_progress";
    public static final String ERROR = "error";
}
