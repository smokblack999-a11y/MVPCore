package serg.chuprin.telegram.core;
public class TelegramClientException extends Exception {
    public TelegramClientException(String message) { super(message); }
    public TelegramClientException(String message, Throwable cause) { super(message,cause); }
}
