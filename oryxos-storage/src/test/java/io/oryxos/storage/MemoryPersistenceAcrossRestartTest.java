package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.storage.repository.MemoryEntryRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

class MemoryPersistenceAcrossRestartTest {

    @TempDir
    Path temporary;

    @Test
    void memoryRemainsAfterJpaContextRestart() {
        String url = "jdbc:sqlite:" + temporary.resolve("memory.db");
        try (var first = application(url).run()) {
            var store = store(first.getBean(MemoryEntryRepository.class));
            store.append(MemoryOwner.agent("alpha"), "restart-memory", MemoryScope.ARCHIVAL);
        }
        try (var second = application(url).run()) {
            assertThat(store(second.getBean(MemoryEntryRepository.class))
                    .load(MemoryOwner.agent("alpha")).archival())
                    .extracting(entry -> entry.content()).containsExactly("restart-memory");
        }
    }

    private static JpaMemoryStore store(MemoryEntryRepository repository) {
        return new JpaMemoryStore(repository, Clock.systemUTC(), 4000, 20, 4000);
    }

    private static SpringApplication application(String url) {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.datasource.url", url,
                "spring.datasource.driver-class-name", "org.sqlite.JDBC",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));
        return application;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(StorageConfiguration.class)
    static class TestApplication {
    }
}
