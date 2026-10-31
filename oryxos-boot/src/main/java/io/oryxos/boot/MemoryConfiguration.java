package io.oryxos.boot;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.memory.DefaultMemoryService;
import io.oryxos.memory.MarkdownMemoryStore;
import io.oryxos.memory.MemoryOwnerResolver;
import io.oryxos.memory.MemoryTools;
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
    @ConditionalOnProperty(prefix = "oryxos.memory", name = "backend",
            havingValue = "markdown", matchIfMissing = true)
    LongTermMemoryStore markdownMemoryStore(@Value("${oryxos.workspace:./.oryxos}") String workspace,
            MemoryProperties properties) {
        return new MarkdownMemoryStore(Path.of(workspace), Clock.systemDefaultZone(), ZoneId.systemDefault(),
                properties.getArchivalMaxCharacters(), properties.getRecallMaxResults(),
                properties.getEntryMaxCharacters());
    }

    @Bean
    @ConditionalOnMissingBean
    MemoryOwnerResolver memoryOwnerResolver() {
        return new MemoryOwnerResolver();
    }

    @Bean
    MemoryService memoryService(LongTermMemoryStore store, MemoryOwnerResolver owners) {
        return new DefaultMemoryService(store, owners);
    }

    @Bean
    MemoryTools memoryTools(MemoryService memory) {
        return new MemoryTools(memory);
    }
}
