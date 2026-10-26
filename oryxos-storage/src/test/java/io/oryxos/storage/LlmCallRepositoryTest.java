package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallStatus;
import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.storage.repository.LlmCallRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = LlmCallRepositoryTest.TestApplication.class)
class LlmCallRepositoryTest {

    private static final Path DATABASE = Path.of(System.getProperty("java.io.tmpdir"),
            "oryxos-repository-" + UUID.randomUUID() + ".db");

    @Autowired
    private JpaLlmCallRecorder recorder;

    @Autowired
    private LlmCallRepository repository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.community.dialect.SQLiteDialect");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/sqlite");
    }

    @Test
    void flywaySchemaPersistsAndReadsEveryAuditField() {
        Instant createdAt = Instant.parse("2026-10-22T00:00:00Z");
        LlmCallRecord saved = recorder.record(new LlmCallRecord(null, "session-1", "weather", "qwen",
                "qwen-plus", 10, 4, 14, true, 37, LlmCallStatus.FAILED,
                "NETWORK_ERROR", "network unavailable", createdAt));

        assertThat(saved.id()).isNotNull();
        assertThat(repository.findById(saved.id())).isPresent().get()
                .extracting(entity -> entity.toRecord())
                .satisfies(record -> {
                    assertThat(record.id()).isEqualTo(saved.id());
                    assertThat(record.sessionId()).isEqualTo("session-1");
                    assertThat(record.profileName()).isEqualTo("weather");
                    assertThat(record.provider()).isEqualTo("qwen");
                    assertThat(record.model()).isEqualTo("qwen-plus");
                    assertThat(record.promptTokens()).isEqualTo(10);
                    assertThat(record.completionTokens()).isEqualTo(4);
                    assertThat(record.totalTokens()).isEqualTo(14);
                    assertThat(record.usageKnown()).isTrue();
                    assertThat(record.durationMs()).isEqualTo(37);
                    assertThat(record.status()).isEqualTo(LlmCallStatus.FAILED);
                    assertThat(record.errorCode()).isEqualTo("NETWORK_ERROR");
                    assertThat(record.errorMessage()).isEqualTo("network unavailable");
                    assertThat(record.createdAt()).isEqualTo(createdAt);
                });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(StorageConfiguration.class)
    static class TestApplication {
    }
}
