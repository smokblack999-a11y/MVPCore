package telegram.core.tdlib;

import com.google.gson.JsonObject;
import telegram.core.api.AuthorizationState;

/** Maps pinned TDLib authorization states to the stable public API. */
final class AuthorizationStateMapper {
    private AuthorizationStateMapper() { }

    static AuthorizationState map(JsonObject auth) {
        if (auth == null || !auth.has("@type")) {
            return new AuthorizationState(AuthorizationState.Type.UNKNOWN);
        }
        String type = auth.get("@type").getAsString();
        AuthorizationState.Type mapped;
        switch (type) {
            case "authorizationStateWaitPhoneNumber":
                mapped = AuthorizationState.Type.WAIT_PHONE; break;
            case "authorizationStateWaitPremiumPurchase":
                mapped = AuthorizationState.Type.WAIT_PREMIUM_PURCHASE; break;
            case "authorizationStateWaitEmailAddress":
                mapped = AuthorizationState.Type.WAIT_EMAIL; break;
            case "authorizationStateWaitEmailCode":
                mapped = AuthorizationState.Type.WAIT_EMAIL_CODE; break;
            case "authorizationStateWaitCode":
                mapped = AuthorizationState.Type.WAIT_CODE; break;
            case "authorizationStateWaitOtherDeviceConfirmation":
                mapped = AuthorizationState.Type.WAIT_OTHER_DEVICE_CONFIRMATION; break;
            case "authorizationStateWaitPassword":
                mapped = AuthorizationState.Type.WAIT_PASSWORD; break;
            case "authorizationStateWaitRegistration":
                mapped = AuthorizationState.Type.WAIT_REGISTRATION; break;
            case "authorizationStateReady":
                mapped = AuthorizationState.Type.READY; break;
            case "authorizationStateLoggingOut":
                mapped = AuthorizationState.Type.LOGGING_OUT; break;
            case "authorizationStateClosing":
                mapped = AuthorizationState.Type.CLOSING; break;
            case "authorizationStateClosed":
                mapped = AuthorizationState.Type.CLOSED; break;
            default:
                mapped = AuthorizationState.Type.UNKNOWN;
        }
        return new AuthorizationState(mapped, type);
    }
}
