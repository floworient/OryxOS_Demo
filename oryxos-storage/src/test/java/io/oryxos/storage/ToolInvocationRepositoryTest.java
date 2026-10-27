package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.audit.ToolInvocationRecord;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class ToolInvocationRepositoryTest {

    @TempDir
    Path temporary;

    @Test
    void upgradesV1AndPersistsEverySuccessAndFailureFieldAcrossRestart() {
        String url = "jdbc:sqlite:" + temporary.resolve("audit.db");
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration/sqlite")
                .target(MigrationVersion.fromVersion("1")).load().migrate();
        try (var first = application(url).run()) {
            var recorder = first.getBean(ToolInvocationRecorder.class);
            recorder.record(new ToolInvocationRecord("session-a", "lookup", "{\"q\":1}", "{\"ok\":true}",
                    true, null, 42, Instant.parse("2026-10-26T00:00:00Z")));
            recorder.record(new ToolInvocationRecord("session-a", "lookup", "{}", "{}",
                    false, "拒绝", 0, Instant.parse("2026-10-26T01:00:00Z")));
        }
        try (var second = application(url).run()) {
            var jdbc = second.getBean(JdbcTemplate.class);
            var rows = jdbc.queryForList("select * from tool_invocations order by id");
            assertThat(rows).hasSize(2);
            assertThat(rows.getFirst()).containsEntry("session_id", "session-a")
                    .containsEntry("tool_name", "lookup").containsEntry("input_json", "{\"q\":1}")
                    .containsEntry("result_json", "{\"ok\":true}").containsEntry("success", 1)
                    .containsEntry("duration_ms", 42).containsEntry("error_message", null);
            assertThat(rows.getFirst().get("created_at")).isNotNull();
            assertThat(rows.getLast()).containsEntry("success", 0).containsEntry("error_message", "拒绝");
        }
    }

    private static SpringApplication application(String url) {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.datasource.url", url, "spring.datasource.driver-class-name", "org.sqlite.JDBC",
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
