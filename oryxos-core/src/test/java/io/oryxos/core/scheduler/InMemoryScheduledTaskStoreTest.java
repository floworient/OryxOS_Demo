package io.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.oryxos.core.profile.Profile;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryScheduledTaskStoreTest {

    @Test
    void definitionIsDerivedFromProfileAndRejectsInconsistentIdentity() {
        Profile profile = profile("weather");
        Profile.ScheduleRef schedule = new Profile.ScheduleRef(
                "daily", "0 0 8 * * *", "Asia/Shanghai", "生成天气日报");

        ScheduledTaskDefinition definition = ScheduledTaskDefinition.from(profile, schedule);

        assertThat(definition.scheduleId()).isEqualTo(ScheduleId.from("weather", "daily"));
        assertThat(definition.profileName()).isEqualTo("weather");
        assertThat(definition.scheduleKey()).isEqualTo("daily");
        assertThat(definition.cron()).isEqualTo("0 0 8 * * *");
        assertThat(definition.zoneId()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(definition.message()).isEqualTo("生成天气日报");

        assertThatIllegalArgumentException().isThrownBy(() -> new ScheduledTaskDefinition(
                ScheduleId.from("another", "daily"),
                "weather", "daily", "0 0 8 * * *", ZoneId.of("UTC"), "message"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ScheduledTaskDefinition(
                ScheduleId.from("weather", "daily"),
                "weather", "daily", "not-a-cron", ZoneId.of("UTC"), "message"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ScheduledTaskDefinition(
                ScheduleId.from("weather", "daily"),
                "weather", "daily", "0 0 8 * * *", ZoneId.of("UTC"), " "));
    }

    @Test
    void executionEnforcesTimelineAndOutcomeInvariants() {
        ScheduleId id = ScheduleId.from("weather", "daily");
        Instant started = Instant.parse("2026-11-04T00:00:00Z");
        Instant finished = started.plusSeconds(2);

        TaskExecution success = TaskExecution.succeeded(id, null, started, finished);
        TaskExecution failure = TaskExecution.failed(
                id, "session-1", started, finished, new IllegalStateException("sensitive detail"));

        assertThat(success.success()).isTrue();
        assertThat(success.errorType()).isNull();
        assertThat(failure.success()).isFalse();
        assertThat(failure.errorType()).isEqualTo("IllegalStateException");
        assertThat(failure.toString()).doesNotContain("sensitive detail");

        assertThatIllegalArgumentException().isThrownBy(() ->
                TaskExecution.succeeded(id, "session-1", finished, started));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new TaskExecution(id, "session-1", started, finished, true, "Unexpected"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new TaskExecution(id, "session-1", started, finished, false, " "));
        assertThatNullPointerException().isThrownBy(() ->
                TaskExecution.failed(id, "session-1", started, finished, null));
    }

    @Test
    void reconcileReplacesDefinitionAndRetireKeepsImmutableHistory() {
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        ScheduleId id = ScheduleId.from("weather", "daily");
        ScheduledTaskDefinition original = definition(id, "0 0 8 * * *", "first");
        ScheduledTaskDefinition replacement = definition(id, "0 30 8 * * *", "second");
        TaskExecution execution = TaskExecution.succeeded(
                id, "session-1", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

        assertThat(store.reconcile(original)).isEqualTo(original);
        assertThat(store.reconcile(replacement)).isEqualTo(replacement);
        assertThat(store.find(id)).contains(replacement);
        store.recordExecution(execution);

        List<TaskExecution> snapshot = store.executions(id);
        assertThat(snapshot).containsExactly(execution);
        assertThat(snapshot).isUnmodifiable();

        store.retire(id);
        store.retire(id);

        assertThat(store.find(id)).isEmpty();
        assertThat(store.executions(id)).containsExactly(execution);
        assertThatNullPointerException().isThrownBy(() -> store.reconcile(null));
        assertThatNullPointerException().isThrownBy(() -> store.retire(null));
        assertThatNullPointerException().isThrownBy(() -> store.find(null));
        assertThatNullPointerException().isThrownBy(() -> store.recordExecution(null));
        assertThatNullPointerException().isThrownBy(() -> store.executions(null));
    }

    @Test
    void reconciliationPreservesDisabledStatusAndRetiredHistory() {
        var store = new InMemoryScheduledTaskStore();
        var id = ScheduleId.from("weather", "daily");
        var start = Instant.parse("2026-11-09T00:00:00Z");
        store.reconcile(definition(id, "0 0 8 * * *", "first"));
        assertThat(store.isEnabled(id)).isTrue();
        assertThat(store.list().getFirst().runCount()).isZero();

        store.setEnabled(id, false);
        store.recordExecution(TaskExecution.failed(
                id, "session-1", start, start.plusMillis(15),
                new IllegalStateException("secret=must-not-appear")));
        store.retire(id);
        assertThat(store.list()).isEmpty();
        assertThat(store.exists(id)).isTrue();
        assertThat(store.executionViews(id)).singleElement().satisfies(history -> {
            assertThat(history.success()).isFalse();
            assertThat(history.errorMessage()).isNotBlank().doesNotContain("must-not-appear");
            assertThat(history.durationMs()).isEqualTo(15);
        });

        store.reconcile(definition(id, "0 30 8 * * *", "second"));
        store.updateNextRun(id, start.plusSeconds(30));
        assertThat(store.list()).singleElement().satisfies(view -> {
            assertThat(view.scheduleId()).isEqualTo(id);
            assertThat(view.enabled()).isFalse();
            assertThat(view.lastStatus()).isEqualTo("failed");
            assertThat(view.lastRunAt()).isEqualTo(start);
            assertThat(view.runCount()).isEqualTo(1);
            assertThat(view.nextRunAt()).isEqualTo(start.plusSeconds(30));
        });
    }

    @Test
    void concurrentExecutionAppendDoesNotLoseRecords() throws Exception {
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        ScheduleId id = ScheduleId.from("weather", "daily");
        int threadCount = 8;
        int executionsPerThread = 100;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int thread = 0; thread < threadCount; thread++) {
                int threadNumber = thread;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < executionsPerThread; index++) {
                        Instant time = Instant.ofEpochMilli((long) threadNumber * executionsPerThread + index);
                        store.recordExecution(TaskExecution.succeeded(
                                id, "session-" + threadNumber + '-' + index, time, time));
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(store.executions(id)).hasSize(threadCount * executionsPerThread);
    }

    private static ScheduledTaskDefinition definition(ScheduleId id, String cron, String message) {
        return new ScheduledTaskDefinition(
                id, "weather", "daily", cron, ZoneId.of("UTC"), message);
    }

    private static Profile profile(String name) {
        return new Profile(
                name,
                "description",
                new Profile.Identity(name, "prompt"),
                new Profile.ProviderRef("provider", "model", null),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new Profile.Settings(8, 20),
                Path.of(".", name, "AGENT.md"));
    }
}
