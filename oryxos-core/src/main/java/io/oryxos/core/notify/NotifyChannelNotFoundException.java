package io.oryxos.core.notify;

public class NotifyChannelNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotifyChannelNotFoundException(String message) {
        super(message);
    }
}
