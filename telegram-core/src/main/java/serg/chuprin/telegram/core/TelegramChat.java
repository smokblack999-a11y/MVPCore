package serg.chuprin.telegram.core;
public final class TelegramChat {
    public final long id;
    public final String title;
    public final boolean group;
    public final boolean channel;
    public TelegramChat(long id, String title, boolean group, boolean channel) {
        this.id = id; this.title = title == null ? "" : title; this.group = group; this.channel = channel;
    }
}
