package io.oryxos.web.api;

/** 将会话基础设施异常标记为可统一脱敏的 Web 应用错误。 */
public final class SessionStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessionStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
