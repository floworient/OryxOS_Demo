package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.session.SessionManager;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class SessionSummaryQueryTest {
    @TempDir
    Path temporary;

    @Test
    void summariesIncludeActiveAndArchivedRowsInStableOrderWithoutDecodingMessages() {
        try (var context = application().run()) {
            var jdbc = context.getBean(JdbcTemplate.class);
            Instant older = Instant.parse("2026-10-27T08:00:00Z");
            Instant newer = Instant.parse("2026-10-28T08:00:00Z");
            insert(jdbc, "b-session", "weather", "cli", "bob", "active", newer);
            insert(jdbc, "a-session", "digest", "web", "alice", "active", newer);
            insert(jdbc, "c-session", "archive", "cli", "carol", "archived", older.plusSeconds(172800));

            var summaries = context.getBean(SessionManager.class).list();

            assertThat(summaries).extracting(summary -> summary.id())
                    .containsExactly("c-session", "a-session", "b-session");
            assertThat(summaries.get(0).profileName()).isEqualTo("archive");
            assertThat(summaries.get(0).status()).isEqualTo("archived");
            assertThat(summaries.get(0).lastActiveAt()).isAfter(newer);
        }
    }

    private static void insert(JdbcTemplate jdbc, String id, String profile, String channel,
            String user, String status, Instant activeAt) {
        Timestamp time = Timestamp.from(activeAt);
        jdbc.update("""
                insert into sessions(session_id, profile_name, channel, user_id, messages_json,
                    status, created_at, last_active_at, archived_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, profile, channel, user, "{损坏的历史", status, time, time,
                "archived".equals(status) ? time : null);
    }

    private SpringApplication application() {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.datasource.url", "jdbc:sqlite:" + temporary.resolve("sessions.db"),
                "spring.datasource.driver-class-name", "org.sqlite.JDBC",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));
        return application;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(StorageConfiguration.class)
    static class TestApplication { }
}
