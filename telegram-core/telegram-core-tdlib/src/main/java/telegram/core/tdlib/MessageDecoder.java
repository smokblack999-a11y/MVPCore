package telegram.core.tdlib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import telegram.core.api.TelegramClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MessageDecoder {
    static TelegramClient.Message decode(JsonObject message) {
        if (message == null) return new TelegramClient.Message(0, 0, "");
        long id = getLong(message, "id");
        long chatId = getLong(message, "chat_id");
        return new TelegramClient.Message(id, chatId, extractText(message));
    }

    static List<TelegramClient.Message> decodeList(JsonObject response) {
        if (response == null || !response.has("messages")) return Collections.emptyList();
        JsonArray array = response.getAsJsonArray("messages");
        List<TelegramClient.Message> result = new ArrayList<>(array.size());
        for (JsonElement element : array) if (element.isJsonObject()) result.add(decode(element.getAsJsonObject()));
        return result;
    }

    static String extractText(JsonObject message) {
        if (message.has("content") && message.get("content").isJsonObject()) {
            JsonObject content = message.getAsJsonObject("content");
            String type = content.has("@type") ? content.get("@type").getAsString() : "";
            if ("messageText".equals(type) && content.has("text")) return formattedText(content.getAsJsonObject("text"));
            if ("messagePhoto".equals(type) && content.has("caption")) return formattedText(content.getAsJsonObject("caption"));
            if ("messageVideo".equals(type) && content.has("caption")) return formattedText(content.getAsJsonObject("caption"));
            if ("messageDocument".equals(type) && content.has("caption")) return formattedText(content.getAsJsonObject("caption"));
        }
        return "";
    }

    private static String formattedText(JsonObject value) {
        return value != null && value.has("text") ? value.get("text").getAsString() : "";
    }

    private static long getLong(JsonObject object, String name) {
        try { return object.has(name) ? object.get(name).getAsLong() : 0L; }
        catch (RuntimeException ignored) { return 0L; }
    }
}
