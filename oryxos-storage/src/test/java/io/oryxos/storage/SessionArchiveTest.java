package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.session.SessionManager;
import io.oryxos.core.session.SessionStatus;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class SessionArchiveTest {

    @TempDir
    Path temporary;

    @Test
    void archivePreservesHistoryAndAllowsANewGenerationWithoutRevival() {
        String url = "jdbc:sqlite:" + temporary.resolve("archive.db");
        try (var context = application(url).run()) {
            var sessions = context.getBean(SessionManager.class);
            var firstAcquisition = sessions.acquire("web", "alice", "weather");
            var original = sessions.find(firstAcquisition.sessionId()).orElseThrow();
            assertThat(firstAcquisition.created()).isTrue();
            assertThat(sessions.acquire("web", "alice", "weather").created()).isFalse();
            original.append("旧问题");
            sessions.save(original);

            assertThat(sessions.archive(original.id())).isTrue();
            assertThat(sessions.archive(original.id())).isFalse();
            assertThat(sessions.find(original.id())).isEmpty();
            assertThat(sessions.details(original.id())).get().satisfies(details -> {
                assertThat(details.status()).isEqualTo(SessionStatus.ARCHIVED);
                assertThat(details.archivedAt()).isNotNull();
                assertThat(details.messages()).singleElement()
                        .satisfies(message -> assertThat(message.getText()).isEqualTo("旧问题"));
            });
            assertThatThrownBy(() -> sessions.save(original))
                    .isInstanceOf(IllegalStateException.class);

            var replacement = sessions.getOrCreate("web", "alice", "weather");
            assertThat(replacement.id()).isNotEqualTo(original.id());
            assertThat(replacement.messages()).isEmpty();
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                    "select count(*) from sessions where status='active'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from sessions where status='archived'", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void restoreRevivesArchivedSessionWithHistoryAndClearsArchivedAt() {
        String url = "jdbc:sqlite:" + temporary.resolve("restore.db");
        try (var context = application(url).run()) {
            var sessions = context.getBean(SessionManager.class);
            var acquisition = sessions.acquire("web", "alice", "weather");
            var original = sessions.find(acquisition.sessionId()).orElseThrow();
            original.append("历史消息");
            sessions.save(original);
            assertThat(sessions.archive(original.id())).isTrue();

            // 恢复后：状态回到活动、归档时间清空、历史消息保留
            assertThat(sessions.restore(original.id())).isTrue();
            assertThat(sessions.restore(original.id())).isFalse();
            assertThat(sessions.find(original.id())).get().satisfies(session -> {
                assertThat(session.messages()).singleElement()
                        .satisfies(message -> assertThat(message.getText()).isEqualTo("历史消息"));
            });
            assertThat(sessions.details(original.id())).get().satisfies(details -> {
                assertThat(details.status()).isEqualTo(SessionStatus.ACTIVE);
                assertThat(details.archivedAt()).isNull();
            });
            // 恢复后的会话可继续写入
            var revived = sessions.find(original.id()).orElseThrow();
            revived.append("恢复后的新消息");
            sessions.save(revived);
            assertThat(sessions.find(original.id()).orElseThrow().messages()).hasSize(2);
        }
    }

    @Test
    void concurrentGetOrCreateKeepsOneActiveGeneration() throws Exception {
        String url = "jdbc:sqlite:" + temporary.resolve("concurrent.db");
        try (var context = application(url).run(); var executor = Executors.newFixedThreadPool(8)) {
            var sessions = context.getBean(SessionManager.class);
            Callable<String> request = () -> sessions.getOrCreate("web", "alice", "weather").id();
            var futures = executor.invokeAll(java.util.Collections.nCopies(8, request));
            var ids = new HashSet<String>();
            for (var future : futures) {
                ids.add(future.get());
            }
            assertThat(ids).hasSize(1);
            assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                    "select count(*) from sessions where status='active'", Integer.class)).isEqualTo(1);
        }
    }

    static SpringApplication application(String url) {
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
    static class TestApplication { }
}
