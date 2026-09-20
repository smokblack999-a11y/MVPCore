package telegram.core.tdlib;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class TdLibTransportConfigTest {
    @Test
    void clonesDatabaseEncryptionKey() {
        byte[] key = new byte[32];
        key[0] = 7;
        TdLibTransportConfig config = new TdLibTransportConfig(
                12345, "hash", "/db", "/files", key,
                "ru", "Test", "Android", "1.0"
        );
        key[0] = 99;
        assertEquals(7, config.databaseEncryptionKey[0]);
    }

    @Test
    void rejectsInvalidDatabaseKeyLength() {
        assertThrows(IllegalArgumentException.class, () ->
                new TdLibTransportConfig(
                        12345, "hash", "/db", "/files", new byte[31],
                        "en", "Test", "Android", "1.0"
                ));
    }

    @Test
    void appliesSafeDefaultsForOptionalDeviceMetadata() {
        TdLibTransportConfig config = new TdLibTransportConfig(
                12345, "hash", "/db", "/files", new byte[32],
                " ", "", null, ""
        );
        assertEquals("en", config.systemLanguageCode);
        assertEquals("Samurai Telegram Client", config.deviceModel);
        assertEquals("unknown", config.systemVersion);
        assertEquals("0.1.0", config.applicationVersion);
    }
}
