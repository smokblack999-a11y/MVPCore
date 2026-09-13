package telegram.core.tdlib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChatListDecoder {
    List<Long> ids(JsonObject response) {
        if (response == null || !response.has("chat_ids")) return Collections.emptyList();
        JsonArray array = response.getAsJsonArray("chat_ids");
        List<Long> ids = new ArrayList<>(array.size());
        for (JsonElement element : array) if (element.isJsonPrimitive()) ids.add(element.getAsLong());
        return ids;
    }
}
