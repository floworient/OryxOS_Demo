package io.oryxos.tool.mcp;

public final class McpConnectionException extends RuntimeException {
    public McpConnectionException(String message) {
        super(message);
    }

    public McpConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
