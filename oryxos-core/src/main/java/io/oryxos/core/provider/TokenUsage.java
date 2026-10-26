package io.oryxos.core.provider;

public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    public static final TokenUsage UNKNOWN = new TokenUsage(null, null, null);

    public boolean isKnown() {
        return promptTokens != null && completionTokens != null && totalTokens != null;
    }
}
