package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionCompactionMigrationTest {

    @TempDir
    Path temporary;

    @Test
    void upgradesV9WithoutChangingOriginalMessagesOrLegacyCallPurpose() throws Exception {
        String url = "jdbc:sqlite:" + temporary.resolve("session.db");
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration/sqlite")
                .target(MigrationVersion.fromVersion("9")).load().migrate();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO sessions(session_id, profile_name, channel, user_id, messages_json,
                        status, created_at, last_active_at, message_count)
                    VALUES ('legacy', 'p', 'cli', 'u', '[{"role":"user","text":"旧消息"}]',
                        'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO llm_calls(profile_name, provider, model, duration_ms, status, created_at)
                    VALUES ('p', 'provider', 'model', 0, 'SUCCESS', CURRENT_TIMESTAMP)
                    """);
        }
        Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration/sqlite").load().migrate();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery(
                    "SELECT context_summary, context_summary_until, messages_json FROM sessions")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isNull();
                assertThat(row.getInt(2)).isZero();
                assertThat(row.getString(3)).contains("旧消息");
            }
            try (var row = statement.executeQuery("SELECT purpose FROM llm_calls")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("CHAT");
            }
        }
    }
}
