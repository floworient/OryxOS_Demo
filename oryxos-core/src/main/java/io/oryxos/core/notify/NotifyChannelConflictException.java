package io.oryxos.core.notify;

public class NotifyChannelConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotifyChannelConflictException(String message) {
        super(message);
    }

    public NotifyChannelConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
