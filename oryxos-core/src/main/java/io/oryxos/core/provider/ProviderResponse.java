package io.oryxos.core.provider;

import java.util.List;

public record ProviderResponse(
        String providerName,
        String model,
        String content,
        TokenUsage usage,
        List<ToolCallIntent> toolCalls,
        long durationMs,
        String finishReason) {
    public ProviderResponse {
        toolCalls = List.copyOf(toolCalls);
    }

    public ProviderResponse(String providerName, String model, String content, TokenUsage usage,
            List<ToolCallIntent> toolCalls, long durationMs) {
        this(providerName, model, content, usage, toolCalls, durationMs, null);
    }
}
