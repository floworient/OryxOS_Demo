package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallStatus;
import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.storage.repository.LlmCallRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

class LlmCallPersistenceAcrossRestartTest {

    @Test
    void recordsRemainAfterSpringJpaContextIsRestarted() throws Exception {
        Path database = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "oryxos-restart-", ".db");
        String url = "jdbc:sqlite:" + database;

        try (var first = application(url).run()) {
            first.getBean(JpaLlmCallRecorder.class).record(new LlmCallRecord(null, null,
                    "weather", "qwen", "qwen-plus", null, null, null, false,
                    5, LlmCallStatus.SUCCESS, null, null, Instant.parse("2026-10-22T00:00:00Z")));
        }

        try (var second = application(url).run()) {
            assertThat(second.getBean(LlmCallRepository.class).findAll())
                    .singleElement()
                    .satisfies(entity -> {
                        assertThat(entity.toRecord().profileName()).isEqualTo("weather");
                        assertThat(entity.toRecord().usageKnown()).isFalse();
                    });
        }
    }

    private static SpringApplication application(String url) {
        var application = new SpringApplication(RestartTestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(java.util.Map.of(
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
    static class RestartTestApplication {
    }
}
