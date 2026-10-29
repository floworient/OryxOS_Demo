package io.oryxos.tool.notify;

public class NotifyDeliveryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotifyDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
