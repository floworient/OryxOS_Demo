package io.oryxos.core.tool;

public record ToolResult(boolean success, String content, String errorMessage, boolean retryable) {
}
