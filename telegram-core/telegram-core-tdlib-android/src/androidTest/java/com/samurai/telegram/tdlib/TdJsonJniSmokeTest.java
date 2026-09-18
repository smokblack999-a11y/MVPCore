package com.samurai.telegram.tdlib;

import org.json.JSONObject;
import org.drinkless.tdlib.JsonClient;
import org.junit.Assert;
import org.junit.Test;

/**
 * Proves that the generated TDLib JSON Java binding is backed by the real
 * libtdjsonjava.so, rather than a stub or reflection-only placeholder.
 */
public final class TdJsonJniSmokeTest {
    @Test
    public void nativeLibraryLoadsAndExecutes() throws Exception {
        int clientId = JsonClient.createClientId();
        Assert.assertTrue("TDLib client id must be non-negative", clientId >= 0);

        String response = JsonClient.execute(
                "{\"@type\":\"getOption\",\"name\":\"version\"}"
        );
        Assert.assertNotNull("TDLib synchronous execute returned null", response);

        JSONObject option = new JSONObject(response);
        Assert.assertEquals(
                "optionValueString",
                option.optString("@type")
        );
        Assert.assertTrue(
                "TDLib version must not be empty",
                option.optString("value").length() > 0
        );

        JsonClient.send(clientId, "{\"@type\":\"getAuthorizationState\"}");
        String update = JsonClient.receive(5.0);
        Assert.assertNotNull("TDLib did not answer getAuthorizationState", update);
        Assert.assertTrue(
                "Unexpected TDLib response: " + update,
                update.contains("authorizationState")
        );

        JsonClient.send(clientId, "{\"@type\":\"close\"}");
    }
}
