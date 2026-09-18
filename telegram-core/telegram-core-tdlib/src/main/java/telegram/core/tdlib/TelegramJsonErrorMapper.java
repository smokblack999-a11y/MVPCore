package telegram.core.tdlib;

import com.google.gson.JsonObject;
import telegram.core.api.TelegramError;

import java.util.Locale;

/** Converts TDLib JSON errors to stable transport errors without logging raw payloads. */
final class TelegramJsonErrorMapper {
    private TelegramJsonErrorMapper() { }

    static TelegramError fromJson(JsonObject json) {
        int code = json != null && json.has("code") ? safeInt(json, "code") : 0;
        String message = json != null && json.has("message")
                ? json.get("message").getAsString() : "TDLib error";
        return classify(code, message);
    }

    static TelegramError fromThrowable(Throwable error) {
        Throwable cause = unwrap(error);
        String message = cause == null || cause.getMessage() == null
                ? "TDLib transport failure" : cause.getMessage();
        return classify(0, message);
    }

    private static TelegramError classify(int code, String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        TelegramError.Code mapped;
        if (code == 401 || lower.contains("authorization required")) {
            mapped = TelegramError.Code.AUTH_REQUIRED;
        } else if (code == 429 || lower.contains("too many requests") || lower.contains("flood")) {
            mapped = TelegramError.Code.RATE_LIMITED;
        } else if (code >= 400 && code < 500 && (
                lower.contains("phone") || lower.contains("code") || lower.contains("password")
                        || lower.contains("auth") || lower.contains("2fa"))) {
            mapped = TelegramError.Code.AUTH_FAILED;
        } else if (code == 400 || lower.contains("invalid") || lower.contains("wrong parameter")) {
            mapped = TelegramError.Code.INVALID_ARGUMENT;
        } else if (lower.contains("permission") || lower.contains("forbidden")) {
            mapped = TelegramError.Code.PERMISSION_DENIED;
        } else if (lower.contains("file") || lower.contains("media") || lower.contains("photo")
                || lower.contains("video") || lower.contains("document")) {
            mapped = TelegramError.Code.MEDIA_FAILED;
        } else if (lower.contains("network") || lower.contains("connection")
                || lower.contains("timeout") || lower.contains("socket")) {
            mapped = TelegramError.Code.NETWORK;
        } else {
            mapped = TelegramError.Code.INTERNAL;
        }
        return new TelegramError(mapped, message);
    }

    private static int safeInt(JsonObject object, String name) {
        try { return object.get(name).getAsInt(); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static Throwable unwrap(Throwable error) {
        if (error == null) return null;
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
