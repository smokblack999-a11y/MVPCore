package telegram.core.tdlib;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import telegram.core.api.AuthorizationState;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AuthorizationStateMapperTest {
    @Test
    void mapsEveryPinnedTdLibAuthorizationState() {
        assertType("authorizationStateWaitTdlibParameters", AuthorizationState.Type.WAIT_TDLIB_PARAMETERS);
        assertType("authorizationStateWaitPhoneNumber", AuthorizationState.Type.WAIT_PHONE);
        assertType("authorizationStateWaitPremiumPurchase", AuthorizationState.Type.WAIT_PREMIUM_PURCHASE);
        assertType("authorizationStateWaitEmailAddress", AuthorizationState.Type.WAIT_EMAIL);
        assertType("authorizationStateWaitEmailCode", AuthorizationState.Type.WAIT_EMAIL_CODE);
        assertType("authorizationStateWaitCode", AuthorizationState.Type.WAIT_CODE);
        assertType("authorizationStateWaitOtherDeviceConfirmation", AuthorizationState.Type.WAIT_OTHER_DEVICE_CONFIRMATION);
        assertType("authorizationStateWaitPassword", AuthorizationState.Type.WAIT_PASSWORD);
        assertType("authorizationStateWaitRegistration", AuthorizationState.Type.WAIT_REGISTRATION);
        assertType("authorizationStateReady", AuthorizationState.Type.READY);
        assertType("authorizationStateLoggingOut", AuthorizationState.Type.LOGGING_OUT);
        assertType("authorizationStateClosing", AuthorizationState.Type.CLOSING);
        assertType("authorizationStateClosed", AuthorizationState.Type.CLOSED);
    }

    @Test
    void unknownStateNeverBecomesReady() {
        JsonObject state = JsonParser.parseString(
                "{\"@type\":\"authorizationStateFuture\"}").getAsJsonObject();
        assertEquals(AuthorizationState.Type.UNKNOWN, AuthorizationStateMapper.map(state).type);
    }

    private static void assertType(String type, AuthorizationState.Type expected) {
        JsonObject state = new JsonObject();
        state.addProperty("@type", type);
        AuthorizationState mapped = AuthorizationStateMapper.map(state);
        assertEquals(expected, mapped.type, type);
        assertEquals(type, mapped.detail, type);
    }
}
