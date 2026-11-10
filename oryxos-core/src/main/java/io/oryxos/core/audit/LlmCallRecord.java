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
        Instant createdAt,
        LlmCallPurpose purpose) {

    public LlmCallRecord(Long id, String sessionId, String profileName, String provider, String model,
            Integer promptTokens, Integer completionTokens, Integer totalTokens, boolean usageKnown,
            long durationMs, LlmCallStatus status, String errorCode, String errorMessage, Instant createdAt) {
        this(id, sessionId, profileName, provider, model, promptTokens, completionTokens, totalTokens,
                usageKnown, durationMs, status, errorCode, errorMessage, createdAt, LlmCallPurpose.CHAT);
    }
}
