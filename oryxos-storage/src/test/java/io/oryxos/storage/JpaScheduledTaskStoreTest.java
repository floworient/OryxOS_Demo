package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskDefinition;
import io.oryxos.core.scheduler.ScheduledTaskStore;
import io.oryxos.core.scheduler.TaskExecution;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class JpaScheduledTaskStoreTest {

    @TempDir
    Path temporary;

    @Test
    void upgradesV7AndPreservesStateAcrossReconciliation() throws Exception {
        String url = "jdbc:sqlite:" + temporary.resolve("schedules.db");
        var v7 = Flyway.configure().dataSource(url, null, null)
                .locations("classpath:db/migration/sqlite").target("7").load();
        v7.migrate();
        new JdbcTemplate(v7.getConfiguration().getDataSource()).update("""
                insert into sessions(session_id, profile_name, channel, user_id, messages_json,
                    status, created_at, last_active_at)
                values ('existing','weather','cli','user','[]','active',0,0)
                """);
        var definition = definition();
        var id = definition.scheduleId();
        try (var context = application(url).run()) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select session_id from sessions", String.class)).isEqualTo("existing");
            var store = context.getBean(ScheduledTaskStore.class);
            store.reconcile(definition);
            assertThat(store.isEnabled(id)).isTrue();
            try (var executor = Executors.newFixedThreadPool(2)) {
                var toggle = executor.submit(() -> store.setEnabled(id, false));
                var complete = executor.submit(() -> store.recordExecution(
                        TaskExecution.succeeded(id, "existing", Instant.EPOCH, Instant.EPOCH.plusMillis(42))));
                assertThat(toggle.get(10, TimeUnit.SECONDS)).isPresent();
                complete.get(10, TimeUnit.SECONDS);
            }
            store.retire(id);
            assertThat(store.list()).isEmpty();
            assertThat(store.exists(id)).isTrue();
        }
        try (var context = application(url).run()) {
            var store = context.getBean(ScheduledTaskStore.class);
            assertThat(store.reconcile(definition).scheduleId()).isEqualTo(id);
            assertThat(store.list()).singleElement().satisfies(view -> {
                assertThat(view.enabled()).isFalse();
                assertThat(view.runCount()).isEqualTo(1);
                assertThat(view.lastStatus()).isEqualTo("success");
            });
            assertThat(store.executionViews(id)).singleElement().satisfies(execution -> {
                assertThat(execution.sessionId()).isEqualTo("existing");
                assertThat(execution.durationMs()).isEqualTo(42);
            });
        }
    }

    @Test
    void failedStateWriteRollsBackHistoryAndConstraintsRejectInvalidRows() {
        String url = "jdbc:sqlite:" + temporary.resolve("rollback.db");
        try (var context = application(url).run()) {
            var store = context.getBean(ScheduledTaskStore.class);
            var definition = definition();
            var id = definition.scheduleId();
            store.reconcile(definition);
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into scheduled_tasks(schedule_id, profile_name, schedule_key, display_name,
                        cron, zone, message, updated_at)
                    values (?, 'weather', 'daily', 'duplicate', '* * * * * *', 'UTC', 'test', 0)
                    """, ScheduleId.from("weather", "different").value()))
                    .hasMessageContaining("UNIQUE");
            assertThatThrownBy(() -> store.recordExecution(TaskExecution.succeeded(
                    ScheduleId.from("missing", "daily"), null, Instant.EPOCH, Instant.EPOCH)))
                    .isInstanceOf(RuntimeException.class);
            jdbc.execute("""
                    create trigger reject_schedule_completion before update of run_count on scheduled_tasks
                    begin select raise(abort, 'completion blocked'); end
                    """);
            assertThatThrownBy(() -> store.recordExecution(
                    TaskExecution.succeeded(id, null, Instant.EPOCH, Instant.EPOCH)))
                    .hasMessageContaining("completion blocked");
            assertThat(store.executionViews(id)).isEmpty();
            assertThat(store.list().getFirst().runCount()).isZero();
        }
    }

    private static ScheduledTaskDefinition definition() {
        return new ScheduledTaskDefinition(ScheduleId.from("weather", "daily"),
                "weather", "daily", "0 0 8 * * *", ZoneId.of("Asia/Shanghai"), "查天气");
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
