package io.oryxos.core.provider;

import java.util.List;

public record ProviderResponse(
        String providerName,
        String model,
        String content,
        TokenUsage usage,
        List<ToolCallIntent> toolCalls,
        long durationMs) {
    public ProviderResponse {
        toolCalls = List.copyOf(toolCalls);
    }
}
