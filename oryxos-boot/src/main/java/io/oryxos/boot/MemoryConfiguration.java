package io.oryxos.boot;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.memory.DefaultMemoryService;
import io.oryxos.memory.MarkdownMemoryStore;
import io.oryxos.memory.MemoryOwnerResolver;
import io.oryxos.memory.MemoryTools;
import io.oryxos.memory.mem0.Mem0Client;
import io.oryxos.memory.mem0.Mem0MemoryStore;
import io.oryxos.memory.mem0.Mem0ProtocolValidator;
import io.oryxos.storage.JpaMemoryStore;
import io.oryxos.storage.repository.MemoryEntryRepository;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfiguration {

    @Bean
    @ConditionalOnMissingBean(LongTermMemoryStore.class)
    @ConditionalOnProperty(prefix = "oryxos.memory", name = "backend",
            havingValue = "markdown", matchIfMissing = true)
    LongTermMemoryStore markdownMemoryStore(@Value("${oryxos.workspace:./.oryxos}") String workspace,
            MemoryProperties properties) {
        return new MarkdownMemoryStore(Path.of(workspace), Clock.systemDefaultZone(),
                ZoneId.of(properties.getZone()),
                properties.getArchivalMaxCharacters(), properties.getRecallMaxResults(),
                properties.getEntryMaxCharacters());
    }

    @Bean
    @ConditionalOnMissingBean(LongTermMemoryStore.class)
    @ConditionalOnProperty(prefix = "oryxos.memory", name = "backend", havingValue = "sqlite")
    LongTermMemoryStore sqliteMemoryStore(MemoryEntryRepository repository,
            MemoryProperties properties) {
        return new JpaMemoryStore(repository, Clock.systemUTC(),
                properties.getArchivalMaxCharacters(), properties.getRecallMaxResults(),
                properties.getEntryMaxCharacters());
    }

    @Bean
    @ConditionalOnMissingBean(LongTermMemoryStore.class)
    @ConditionalOnProperty(prefix = "oryxos.memory", name = "backend", havingValue = "mem0")
    LongTermMemoryStore mem0MemoryStore(MemoryProperties properties) {
        MemoryProperties.Mem0 settings = properties.getMem0();
        requireText(settings.getBaseUrl(), "oryxos.memory.mem0.base-url");
        requireText(settings.getApiKey(), "oryxos.memory.mem0.api-key");
        requirePositive(settings.getConnectTimeout(), "connect-timeout");
        requirePositive(settings.getRequestTimeout(), "request-timeout");
        Mem0Client client = new Mem0Client(URI.create(settings.getBaseUrl()), settings.getApiKey(),
                settings.getConnectTimeout(), settings.getRequestTimeout(),
                settings.getMaxResponseBytes());
        new Mem0ProtocolValidator(client).validate();
        return new Mem0MemoryStore(client, properties.getArchivalMaxCharacters(),
                properties.getRecallMaxResults(), properties.getEntryMaxCharacters());
    }

    @Bean
    @ConditionalOnMissingBean
    MemoryOwnerResolver memoryOwnerResolver() {
        return new MemoryOwnerResolver();
    }

    @Bean
    MemoryService memoryService(LongTermMemoryStore store, MemoryOwnerResolver owners) {
        if (store.capabilities() == MemoryCapabilities.HYBRID_BUILTIN) {
            throw new IllegalStateException("HYBRID_BUILTIN 已为 015 预留，当前版本不能装配");
        }
        return new DefaultMemoryService(store, owners);
    }

    @Bean
    MemoryTools memoryTools(MemoryService memory) {
        return new MemoryTools(memory);
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " 不能为空");
        }
    }

    private static void requirePositive(java.time.Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("oryxos.memory.mem0." + property + " 必须大于 0");
        }
    }
}
