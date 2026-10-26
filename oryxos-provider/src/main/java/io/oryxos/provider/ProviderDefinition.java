package io.oryxos.provider;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ProviderDefinition(
        String name,
        Type type,
        String model,
        String baseUrl,
        @JsonIgnore String apiKey) {

    public enum Type {
        DASHSCOPE,
        OPENAI_COMPATIBLE
    }

    @Override
    public String toString() {
        return "ProviderDefinition[name=" + name + ", type=" + type + ", model=" + model
                + ", baseUrl=" + baseUrl + ", apiKey=<redacted>]";
    }
}
