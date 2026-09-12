package telegram.core.api;

public final class SendOptions {
    public final String caption;
    public final boolean disableNotification;

    public SendOptions(String caption, boolean disableNotification) {
        this.caption = caption == null ? "" : caption;
        this.disableNotification = disableNotification;
    }
}
