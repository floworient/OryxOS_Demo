package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionMigrationV7Test {

    @TempDir
    Path temporary;

    @Test
    void backfillsLegalArraysAndKeepsBrokenLegacyHistoryQueryable() throws Exception {
        String url = "jdbc:sqlite:" + temporary.resolve("migration-v7.db");
        Flyway.configure().dataSource(url, null, null)
                .locations("classpath:db/migration/sqlite")
                .target(MigrationVersion.fromVersion("6")).load().migrate();
        try (var connection = DriverManager.getConnection(url)) {
            String legalHistory = "[{\"role\":\"user\",\"text\":\"a\"},"
                    + "{\"role\":\"assistant\",\"text\":\"b\",\"calls\":[]}]";
            connection.createStatement().executeUpdate(insert("legal", legalHistory));
            connection.createStatement().executeUpdate(insert("broken", "{broken"));
        }

        Flyway flyway = Flyway.configure().dataSource(url, null, null)
                .locations("classpath:db/migration/sqlite").target(MigrationVersion.fromVersion("7")).load();
        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
        try (var connection = DriverManager.getConnection(url);
                var rows = connection.createStatement().executeQuery(
                        "select session_id, message_count from sessions order by session_id")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("broken");
            assertThat(rows.getInt(2)).isZero();
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("legal");
            assertThat(rows.getInt(2)).isEqualTo(2);
        }
    }

    private static String insert(String id, String messages) {
        return "insert into sessions(session_id, profile_name, channel, user_id, messages_json, "
                + "status, created_at, last_active_at, archived_at) values ('" + id
                + "','p','cli','" + id + "','" + messages.replace("'", "''")
                + "','active','2026-11-08T00:00:00Z','2026-11-08T00:00:00Z',null)";
    }
}
