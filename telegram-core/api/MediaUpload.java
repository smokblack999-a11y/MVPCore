package telegram.core.api;

import java.util.concurrent.CompletableFuture;

/** Host-independent media input. The implementation may use a file, URI adapter, or stream. */
public interface MediaUpload {
    String mimeType();
    String displayName();
    long sizeBytes();
    CompletableFuture<MessageResult> sendTo(long chatId, String caption);

    interface MessageResult {
        long messageId();
        long chatId();
    }
}
