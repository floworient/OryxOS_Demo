package io.oryxos.core.audit;

import java.time.Instant;
import java.util.Objects;

public record ToolInvocationRecord(String sessionId, String toolName, String inputJson,
        String resultJson, boolean success, String errorMessage, long durationMs, Instant createdAt) {

    public ToolInvocationRecord {
        if (sessionId == null || sessionId.isBlank() || toolName == null || toolName.isBlank()
                || inputJson == null || resultJson == null || durationMs < 0
                || !success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("工具审计字段非法");
        }
        Objects.requireNonNull(createdAt);
    }
}
