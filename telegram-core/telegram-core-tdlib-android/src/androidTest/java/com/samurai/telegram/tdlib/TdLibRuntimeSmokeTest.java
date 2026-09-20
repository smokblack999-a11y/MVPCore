package com.samurai.telegram.tdlib;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.json.JSONObject;
import org.junit.Test;

/** Verifies that the generated TDLib JSONJava JNI is real and callable on Android. */
public class TdLibRuntimeSmokeTest {
    @Test
    public void jsonJavaCanExecuteVersionQuery() throws Exception {
        Class<?> jsonClient = Class.forName("org.drinkless.tdlib.JsonClient");
        Method execute = jsonClient.getMethod("execute", String.class);
        Object raw = execute.invoke(null, "{\"@type\":\"getOption\",\"name\":\"version\"}");
        assertNotNull(raw);

        JSONObject response = new JSONObject(String.valueOf(raw));
        assertTrue("TDLib execute() did not return an option", "optionValueString".equals(response.optString("@type")));
        assertTrue("TDLib version is empty", response.optString("value").length() > 0);
        assertTrue("Unexpected TDLib version: " + response.optString("value"), "1.8.67".equals(response.optString("value")));
    }
}
