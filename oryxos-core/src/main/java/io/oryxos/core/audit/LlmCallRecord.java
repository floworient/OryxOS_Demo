package io.oryxos.core.audit;

import java.time.Instant;

public record LlmCallRecord(
        Long id,
        String sessionId,
        String profileName,
        String provider,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean usageKnown,
        long durationMs,
        LlmCallStatus status,
        String errorCode,
        String errorMessage,
        Instant createdAt) {
}
