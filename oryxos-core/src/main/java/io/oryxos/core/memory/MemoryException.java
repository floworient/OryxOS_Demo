package io.oryxos.core.memory;

import java.util.Objects;

public final class MemoryException extends RuntimeException {

    private final Code code;

    public MemoryException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "记忆错误码不能为空");
    }

    public MemoryException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "记忆错误码不能为空");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        INVALID_OWNER,
        INVALID_CONTENT,
        INVALID_SCOPE,
        STORE_UNAVAILABLE,
        MALFORMED_STORE,
        UNSUPPORTED_CAPABILITY
    }
}
