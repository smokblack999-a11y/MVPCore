package telegram.core.tdlib;

/** Minimal RFC 4648 standard Base64 codec for TDLib JSON byte fields. */
final class StandardBase64 {
    private static final char[] TABLE =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private StandardBase64() { }

    static String encode(byte[] input) {
        if (input == null || input.length == 0) return "";
        StringBuilder out = new StringBuilder((input.length + 2) / 3 * 4);
        for (int i = 0; i < input.length; i += 3) {
            int b0 = input[i] & 0xff;
            int b1 = i + 1 < input.length ? input[i + 1] & 0xff : 0;
            int b2 = i + 2 < input.length ? input[i + 2] & 0xff : 0;
            out.append(TABLE[b0 >>> 2]);
            out.append(TABLE[((b0 & 0x03) << 4) | (b1 >>> 4)]);
            out.append(i + 1 < input.length ? TABLE[(b1 & 0x0f) << 2 | (b2 >>> 6)] : '=');
            out.append(i + 2 < input.length ? TABLE[b2 & 0x3f] : '=');
        }
        return out.toString();
    }
}
