package telegram.core.tdlib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import telegram.core.api.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MessageJson {
    private MessageJson() { }

    static Message decode(JsonObject message) {
        if (message == null) return new Message(0L, 0L, "", 0L);
        long id = number(message, "id");
        long chatId = number(message, "chat_id");
        long date = number(message, "date");
        return new Message(id, chatId, text(message), date);
    }

    static List<Message> decodeList(JsonObject response) {
        if (response == null || !response.has("messages") || !response.get("messages").isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray messages = response.getAsJsonArray("messages");
        List<Message> result = new ArrayList<>(messages.size());
        for (JsonElement item : messages) {
            if (item.isJsonObject()) result.add(decode(item.getAsJsonObject()));
        }
        return result;
    }

    private static String text(JsonObject message) {
        if (!message.has("content") || !message.get("content").isJsonObject()) return "";
        JsonObject content = message.getAsJsonObject("content");
        String type = content.has("@type") ? content.get("@type").getAsString() : "";
        if (("messageText".equals(type) || "messagePhoto".equals(type) || "messageVideo".equals(type)
                || "messageDocument".equals(type)) && content.has("text")) {
            return formatted(content.getAsJsonObject("text"));
        }
        if (content.has("caption") && content.get("caption").isJsonObject()) {
            return formatted(content.getAsJsonObject("caption"));
        }
        return "";
    }

    private static String formatted(JsonObject value) {
        return value != null && value.has("text") ? value.get("text").getAsString() : "";
    }

    private static long number(JsonObject object, String name) {
        try { return object.has(name) ? object.get(name).getAsLong() : 0L; }
        catch (RuntimeException ignored) { return 0L; }
    }
}
