package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionMigrationV6Test {

    @TempDir
    Path temporary;

    @Test
    void migrationPreservesV5RowsAndLimitsUniquenessToActiveSessions() throws Exception {
        String url = "jdbc:sqlite:" + temporary.resolve("migration.db");
        Flyway.configure().dataSource(url, null, null)
                .locations("classpath:db/migration/sqlite")
                .target(MigrationVersion.fromVersion("5")).load().migrate();
        String id = "11111111-1111-4111-8111-111111111111";
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.prepareStatement("""
                        insert into sessions(session_id, profile_name, channel, user_id, messages_json,
                            status, created_at, last_active_at, archived_at)
                        values (?, 'weather', 'web', 'alice', '[{"role":"user","text":"旧历史"}]',
                            'active', '2026-11-04T00:00:00Z', '2026-11-04T00:01:00Z', null)
                        """)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }

        Flyway flyway = Flyway.configure().dataSource(url, null, null)
                .locations("classpath:db/migration/sqlite")
                .target(MigrationVersion.fromVersion("6")).load();
        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        try (var connection = DriverManager.getConnection(url)) {
            try (var query = connection.createStatement().executeQuery(
                    "select * from sessions where session_id='" + id + "'")) {
                assertThat(query.next()).isTrue();
                assertThat(query.getString("messages_json")).contains("旧历史");
                assertThat(query.getString("status")).isEqualTo("active");
                assertThat(query.getString("created_at")).startsWith("2026-11-04T00:00:00");
            }
            connection.createStatement().executeUpdate("update sessions set status='archived', "
                    + "archived_at='2026-11-04T00:02:00Z' where session_id='" + id + "'");
            connection.createStatement().executeUpdate("""
                    insert into sessions(session_id, profile_name, channel, user_id, messages_json,
                        status, created_at, last_active_at, archived_at)
                    values ('22222222-2222-4222-8222-222222222222', 'weather', 'web', 'alice', '[]',
                        'active', '2026-11-04T00:03:00Z', '2026-11-04T00:03:00Z', null)
                    """);
            try (var count = connection.createStatement().executeQuery(
                    "select count(*) from sessions where channel='web' and user_id='alice' "
                            + "and profile_name='weather'")) {
                assertThat(count.next()).isTrue();
                assertThat(count.getInt(1)).isEqualTo(2);
            }
        }
    }
}
