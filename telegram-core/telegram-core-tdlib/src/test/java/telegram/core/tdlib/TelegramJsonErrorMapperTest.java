package telegram.core.tdlib;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import telegram.core.api.TelegramError;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TelegramJsonErrorMapperTest {
    @Test
    void preservesTdLibCodeAndMapsAuth() {
        JsonObject json = new JsonObject();
        json.addProperty("@type", "error");
        json.addProperty("code", 401);
        json.addProperty("message", "AUTH_KEY_UNREGISTERED");
        TelegramError error = TelegramJsonErrorMapper.fromJson(json);

        assertEquals(TelegramError.Code.AUTH_REQUIRED, error.code);
        assertEquals(401, error.tdlibCode);
        assertEquals("AUTH_KEY_UNREGISTERED", error.getMessage());
    }

    @Test
    void mapsRateLimit() {
        JsonObject json = new JsonObject();
        json.addProperty("@type", "error");
        json.addProperty("code", 429);
        json.addProperty("message", "Too Many Requests");
        TelegramError error = TelegramJsonErrorMapper.fromJson(json);

        assertEquals(TelegramError.Code.RATE_LIMITED, error.code);
        assertEquals(429, error.tdlibCode);
    }
}
