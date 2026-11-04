package io.oryxos.boot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("oryxos.memory")
public record MemoryProperties(
        @DefaultValue("markdown") String backend,
        @DefaultValue("4000") int archivalMaxCharacters,
        @DefaultValue("20") int recallMaxResults,
        @DefaultValue("4000") int entryMaxCharacters,
        @DefaultValue("Asia/Shanghai") String zone,
        @DefaultValue Mem0 mem0) {

    public record Mem0(
            @DefaultValue("") String baseUrl,
            @DefaultValue("") String apiKey,
            @DefaultValue("3s") Duration connectTimeout,
            @DefaultValue("10s") Duration requestTimeout,
            @DefaultValue("1048576") int maxResponseBytes,
            @DefaultValue("false") boolean archivalInfer) {
    }
}
