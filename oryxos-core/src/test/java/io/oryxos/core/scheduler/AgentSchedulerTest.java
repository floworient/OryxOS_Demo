package io.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.agent.ReActLoop;
import io.oryxos.core.context.PromptBuilder;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.core.tool.ToolSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

@ExtendWith(OutputCaptureExtension.class)
class AgentSchedulerTest {

    @Test
    void profileWithoutSchedulesDoesNotRegisterAnything() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ProfileRegistry profiles = new ProfileRegistry();
        profiles.register(profile("quiet", List.of()));
        AgentService agents = mock(AgentService.class);
        SessionManager sessions = mock(SessionManager.class);

        new AgentScheduler(
                        taskScheduler,
                        profiles,
                        agents,
                        sessions,
                        new InMemoryScheduledTaskStore(),
                        Clock.systemUTC())
                .registerAll();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void registersCronWithZoneAndRunsThroughSharedAgentService() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        ProfileRegistry profiles = new ProfileRegistry();
        Profile profile = profile("weather", List.of(new Profile.ScheduleRef(
                "daily", "0 0 8 * * *", "Asia/Shanghai", "查询天气")));
        profiles.register(profile);
        AgentService agents = mock(AgentService.class);
        SessionManager sessions = mock(SessionManager.class);
        Session session = new Session("scheduler", "scheduler", "weather");
        when(sessions.getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session);
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);
        AgentScheduler scheduler = new AgentScheduler(
                taskScheduler, profiles, agents, sessions, store, clock);

        scheduler.registerAll();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(runnableCaptor.capture(), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue()).isInstanceOf(CronTrigger.class);
        CronTrigger trigger = (CronTrigger) triggerCaptor.getValue();
        assertThat(trigger.getExpression()).isEqualTo("0 0 8 * * *");
        SimpleTriggerContext context = new SimpleTriggerContext(
                Instant.parse("2026-11-03T22:59:00Z"),
                Instant.parse("2026-11-03T22:59:00Z"),
                Instant.parse("2026-11-03T22:59:00Z"));
        assertThat(trigger.nextExecution(context)).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));

        runnableCaptor.getValue().run();
        runnableCaptor.getValue().run();

        verify(sessions, times(2)).getOrCreate("scheduler", "scheduler", "weather");
        verify(agents, times(2)).process(same(session), eq("查询天气"));
        List<TaskExecution> executions = store.executions(ScheduleId.from("weather", "daily"));
        assertThat(executions).hasSize(2).allSatisfy(execution -> {
            assertThat(execution.success()).isTrue();
            assertThat(execution.sessionId()).isEqualTo(session.id());
            assertThat(execution.startedAt()).isEqualTo(clock.instant());
            assertThat(execution.finishedAt()).isEqualTo(clock.instant());
        });
    }

    @Test
    void serializesAllRulesOfOneProfileButAllowsAnotherProfile() throws Exception {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        ProfileRegistry profiles = new ProfileRegistry();
        profiles.register(profile("weather", List.of(
                new Profile.ScheduleRef("morning", "0 0 8 * * *", "UTC", "morning"),
                new Profile.ScheduleRef("evening", "0 0 18 * * *", "UTC", "evening"))));
        profiles.register(profile("news", List.of(
                new Profile.ScheduleRef("daily", "0 0 9 * * *", "UTC", "news"))));
        SessionManager sessions = mock(SessionManager.class);
        when(sessions.getOrCreate(eq("scheduler"), eq("scheduler"), anyString()))
                .thenAnswer(invocation -> session(invocation.getArgument(2)));
        AgentService agents = mock(AgentService.class);
        CountDownLatch weatherEntered = new CountDownLatch(1);
        CountDownLatch releaseWeather = new CountDownLatch(1);
        when(agents.process(any(Session.class), anyString())).thenAnswer(invocation -> {
            Session current = invocation.getArgument(0);
            if (current.profileName().equals("weather") && invocation.getArgument(1).equals("morning")) {
                weatherEntered.countDown();
                releaseWeather.await(10, TimeUnit.SECONDS);
            }
            return "ok";
        });
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        AgentScheduler scheduler = new AgentScheduler(
                taskScheduler, profiles, agents, sessions, store, Clock.systemUTC());
        scheduler.registerAll();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, times(3)).schedule(captor.capture(), any(Trigger.class));
        List<Runnable> callbacks = captor.getAllValues();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> running = executor.submit(callbacks.get(0));
            assertThat(weatherEntered.await(10, TimeUnit.SECONDS)).isTrue();

            callbacks.get(0).run();
            callbacks.get(1).run();
            callbacks.get(2).run();
            releaseWeather.countDown();
            running.get(10, TimeUnit.SECONDS);
        } finally {
            releaseWeather.countDown();
            executor.shutdownNow();
        }

        verify(agents, times(2)).process(any(Session.class), anyString());
        assertThat(store.executions(ScheduleId.from("weather", "morning"))).hasSize(1);
        assertThat(store.executions(ScheduleId.from("weather", "evening"))).isEmpty();
        assertThat(store.executions(ScheduleId.from("news", "daily"))).hasSize(1);
    }

    @Test
    void sessionFailureIsRecordedWithoutLeakingMessageAndDoesNotKeepLock(CapturedOutput output) {
        Fixture fixture = fixtureWithOneSchedule(new InMemoryScheduledTaskStore());
        when(fixture.sessions().getOrCreate("scheduler", "scheduler", "weather"))
                .thenThrow(new IllegalStateException("CANARY-SENSITIVE"))
                .thenReturn(session("weather"));

        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();
        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();

        List<TaskExecution> executions = fixture.memoryStore().executions(ScheduleId.from("weather", "daily"));
        assertThat(executions).hasSize(2);
        assertThat(executions.get(0).sessionId()).isNull();
        assertThat(executions.get(0).errorType()).isEqualTo("IllegalStateException");
        assertThat(executions.get(1).success()).isTrue();
        assertThat(output).doesNotContain("CANARY-SENSITIVE");
    }

    @Test
    void agentFailureIsRecordedAndNextTriggerCanEnter() {
        Fixture fixture = fixtureWithOneSchedule(new InMemoryScheduledTaskStore());
        Session session = session("weather");
        when(fixture.sessions().getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session);
        when(fixture.agents().process(session, "message"))
                .thenThrow(new IllegalArgumentException("private"))
                .thenReturn("ok");

        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();
        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();

        assertThat(fixture.memoryStore().executions(ScheduleId.from("weather", "daily")))
                .extracting(TaskExecution::success)
                .containsExactly(false, true);
    }

    @Test
    void anonymousFailureAndBackwardClockAreSafelyRecorded() {
        Clock clock = mock(Clock.class);
        Instant startedAt = Instant.parse("2026-11-04T00:00:01Z");
        when(clock.instant()).thenReturn(startedAt, startedAt, startedAt.minusSeconds(1));
        Fixture fixture = fixtureWithOneSchedule(new InMemoryScheduledTaskStore(), clock);
        Session session = session("weather");
        when(fixture.sessions().getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session);
        when(fixture.agents().process(session, "message"))
                .thenThrow(new RuntimeException("CANARY") { });

        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();

        assertThat(fixture.memoryStore().executions(ScheduleId.from("weather", "daily")))
                .singleElement()
                .satisfies(execution -> {
                    assertThat(execution.success()).isFalse();
                    assertThat(execution.errorType()).isEqualTo("RuntimeException");
                    assertThat(execution.startedAt()).isEqualTo(startedAt);
                    assertThat(execution.finishedAt()).isEqualTo(startedAt);
                });
    }

    @Test
    void successfulRunClampsBackwardClock() {
        Clock clock = mock(Clock.class);
        Instant startedAt = Instant.parse("2026-11-04T00:00:01Z");
        when(clock.instant()).thenReturn(startedAt, startedAt, startedAt.minusSeconds(1));
        Fixture fixture = fixtureWithOneSchedule(new InMemoryScheduledTaskStore(), clock);
        Session session = session("weather");
        when(fixture.sessions().getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session);

        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();

        assertThat(fixture.memoryStore().executions(ScheduleId.from("weather", "daily")))
                .singleElement()
                .satisfies(execution -> {
                    assertThat(execution.success()).isTrue();
                    assertThat(execution.startedAt()).isEqualTo(startedAt);
                    assertThat(execution.finishedAt()).isEqualTo(startedAt);
                });
    }

    @Test
    void disabledCronSkipsButManualRunRecordsOneExecutionWithoutMovingCron() throws Exception {
        var store = new InMemoryScheduledTaskStore();
        var clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
        var fixture = fixtureWithOneSchedule(store, clock);
        var id = ScheduleId.from("weather", "daily");
        var session = session("weather");
        when(fixture.sessions().getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session);
        assertThat(store.list().getFirst().nextRunAt()).isEqualTo(Instant.parse("2026-11-09T08:00:00Z"));

        store.setEnabled(id, false);
        fixture.callback().run();
        assertThat(store.executions(id)).isEmpty();

        assertThat(fixture.scheduler().runNow(id)).hasValueSatisfying(execution -> {
            assertThat(execution.success()).isTrue();
            assertThat(execution.sessionId()).isEqualTo(session.id());
        });
        assertThat(store.list().getFirst().nextRunAt()).isEqualTo(Instant.parse("2026-11-09T08:00:00Z"));
        assertThat(store.list().getFirst().runCount()).isEqualTo(1);
        assertThatThrownBy(() -> fixture.scheduler().runNow(ScheduleId.from("weather", "missing")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void storeFailureDoesNotEscapeOrKeepProfileLocked() {
        ScheduledTaskStore store = mock(ScheduledTaskStore.class);
        when(store.reconcile(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(store.isEnabled(any())).thenReturn(true);
        doThrow(new IllegalStateException("store-private"))
                .doNothing()
                .when(store).recordExecution(any());
        Fixture fixture = fixtureWithOneSchedule(store);
        Session session = session("weather");
        when(fixture.sessions().getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session);

        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();
        assertThatCode(fixture.callback()::run).doesNotThrowAnyException();

        verify(fixture.agents(), times(2)).process(session, "message");
        verify(store, times(2)).recordExecution(any());
    }

    @Test
    void actualFirstCronTimeWinsWhenRegistrationCrossesCronBoundary() {
        var taskScheduler = mock(TaskScheduler.class);
        var future = mock(ScheduledFuture.class);
        var before = Instant.parse("2026-11-09T07:59:59Z");
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(invocation -> {
            Trigger trigger = invocation.getArgument(1);
            trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(before.plusSeconds(1), ZoneOffset.UTC)));
            return future;
        });
        var profiles = new ProfileRegistry();
        profiles.register(profile("weather", List.of(new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "work"))));
        var store = new InMemoryScheduledTaskStore();
        try (var scheduler = new AgentScheduler(taskScheduler, profiles, mock(AgentService.class),
                mock(SessionManager.class), store, Clock.fixed(before, ZoneOffset.UTC))) {
            scheduler.registerAll();
            assertThat(store.list().getFirst().nextRunAt()).isEqualTo(Instant.parse("2026-11-10T08:00:00Z"));
        }
    }

    @Test
    void timeoutKeepsLockUntilActualToolWorkerExits() throws Exception {
        var release = new CompletableFuture<ToolResult>();
        var tool = mock(OryxTool.class);
        when(tool.getName()).thenReturn("notify");
        when(tool.execute("{}")).thenAnswer(ignored -> release.join());
        var registry = mock(ToolRegistry.class);
        var registration = new ToolRegistration(tool, ToolSource.BUILTIN, "builtin");
        when(registry.visibleTo(any())).thenReturn(List.of(registration));
        var provider = mock(ProviderService.class);
        when(provider.chat(any(), any(), any())).thenReturn(toolResponse());
        try (var tools = new ToolExecutor(registry, (profile, call) -> { }, ignored -> { });
                var scheduler = actualScheduler(provider, tools)) {
            var id = ScheduleId.from("weather", "daily");
            try {
                assertThatThrownBy(() -> scheduler.runNow(id)).isInstanceOf(TimeoutException.class);
                verify(tool).execute("{}");
                Instant deadline = Instant.now().plusMillis(250);
                while (Instant.now().isBefore(deadline)) {
                    assertThat(scheduler.runNow(id)).isEmpty();
                    Thread.sleep(25);
                }
            } finally {
                release.complete(new ToolResult(true, "ok", null, false));
            }
        }
    }

    @Test
    void cancelledProviderCannotRequestLateToolEvenIfItClearsInterrupt() throws Exception {
        var started = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var provider = mock(ProviderService.class);
        var first = new AtomicBoolean(true);
        when(provider.chat(any(), any(), any())).thenAnswer(ignored -> {
            if (first.getAndSet(false)) {
                started.countDown();
                try {
                    stopped.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException cancelled) {
                    // 模拟忽略中断并返回迟到工具请求的 Provider。
                }
                return toolResponse();
            }
            return new ProviderResponse("provider", "model", "完成", TokenUsage.UNKNOWN, List.of(), 0);
        });
        var tools = mock(ToolExecutor.class);
        try (var scheduler = actualScheduler(provider, tools)) {
            var id = ScheduleId.from("weather", "daily");
            try {
                assertThatThrownBy(() -> scheduler.runNow(id)).isInstanceOf(TimeoutException.class);
                assertThat(started.getCount()).isZero();
                var next = scheduler.runNow(id);
                Instant deadline = Instant.now().plusSeconds(5);
                while (next.isEmpty() && Instant.now().isBefore(deadline)) {
                    Thread.sleep(10);
                    next = scheduler.runNow(id);
                }
                assertThat(next).hasValueSatisfying(run -> assertThat(run.success()).isTrue());
                verifyNoInteractions(tools);
            } finally {
                stopped.countDown();
            }
        }
    }

    private static AgentScheduler actualScheduler(ProviderService provider, ToolExecutor tools) {
        var taskScheduler = mock(TaskScheduler.class);
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        var profiles = new ProfileRegistry();
        profiles.register(profile("weather", List.of(new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "work"))));
        var sessions = mock(SessionManager.class);
        when(sessions.getOrCreate("scheduler", "scheduler", "weather")).thenAnswer(ignored -> session("weather"));
        var prompts = mock(PromptBuilder.class);
        when(prompts.prepare(any(), any(), any())).thenReturn(
                new io.oryxos.core.context.PreparedPrompt(new Prompt("work"), 0));
        var agents = new AgentService(profiles, new ReActLoop(prompts, provider, tools), sessions);
        var scheduler = new AgentScheduler(taskScheduler, profiles, agents, sessions,
                new InMemoryScheduledTaskStore(), Clock.systemUTC(), Duration.ofSeconds(2));
        scheduler.registerAll();
        return scheduler;
    }

    private static ProviderResponse toolResponse() {
        return new ProviderResponse("provider", "model", "", TokenUsage.UNKNOWN,
                List.of(new ToolCallIntent("notify-1", "notify", "{}")), 0);
    }

    @Test
    void timeoutRecordsOneFailureAndWorkerRetainsItsProfileLockUntilExit(CapturedOutput output) throws Exception {
        var tasks = mock(TaskScheduler.class);
        doReturn(mock(ScheduledFuture.class)).when(tasks).schedule(any(Runnable.class), any(Trigger.class));
        var profiles = new ProfileRegistry();
        var rule = new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "message");
        profiles.register(profile("weather", List.of(rule)));
        profiles.register(profile("news", List.of(rule)));
        var store = new InMemoryScheduledTaskStore();
        var sessions = mock(SessionManager.class);
        var agents = mock(AgentService.class);
        var weather = session("weather");
        var news = session("news");
        when(sessions.getOrCreate("scheduler", "scheduler", "weather")).thenReturn(weather);
        when(sessions.getOrCreate("scheduler", "scheduler", "news")).thenReturn(news);
        var release = new CompletableFuture<String>();
        when(agents.process(weather, "message")).thenAnswer(ignored -> release.join());
        var weatherId = ScheduleId.from("weather", "daily");
        var newsId = ScheduleId.from("news", "daily");
        try (var scheduler = new AgentScheduler(
                tasks, profiles, agents, sessions, store, Clock.systemUTC(), Duration.ofMillis(500))) {
            scheduler.registerAll();
            try {
                assertThatThrownBy(() -> scheduler.runNow(weatherId)).isInstanceOf(TimeoutException.class);
                assertThat(store.executionViews(weatherId)).singleElement().satisfies(run -> {
                    assertThat(run.success()).isFalse();
                    assertThat(run.sessionId()).isEqualTo(weather.id());
                    assertThat(run.errorMessage()).contains("超时");
                });
                assertThat(scheduler.runNow(weatherId)).isEmpty();
                assertThat(scheduler.runNow(newsId)).hasValueSatisfying(run -> assertThat(run.success()).isTrue());
                assertThat(output.getOut()).contains(weather.id(), news.id());
            } finally {
                release.complete("迟到的回复");
            }
        }
        assertThat(store.executionViews(weatherId)).hasSize(1);
    }

    @Test
    void closeLetsAnAdmittedRunPersistBeforeReturning() throws Exception {
        var store = new InMemoryScheduledTaskStore();
        var fixture = fixtureWithOneSchedule(store);
        var session = session("weather");
        when(fixture.sessions().getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session);
        var started = new CountDownLatch(1);
        var release = new CompletableFuture<String>();
        when(fixture.agents().process(session, "message")).thenAnswer(ignored -> {
            started.countDown();
            return release.join();
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var run = executor.submit(fixture.callback());
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var shutdown = executor.submit(fixture.scheduler()::close);
                assertThatThrownBy(() -> shutdown.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.complete("完成");
                shutdown.get(5, TimeUnit.SECONDS);
                run.get(5, TimeUnit.SECONDS);
            } finally {
                release.complete("完成");
            }
        }
        assertThat(store.executionViews(ScheduleId.from("weather", "daily"))).singleElement()
                .satisfies(execution -> assertThat(execution.success()).isTrue());
    }

    @Test
    void startupRetiresRemovedDefinitionsButShutdownPreservesActiveState() {
        var store = new InMemoryScheduledTaskStore();
        var oldProfile = profile("old", List.of(
                new Profile.ScheduleRef("removed", "0 0 8 * * *", "UTC", "old")));
        var removed = ScheduledTaskDefinition.from(oldProfile, oldProfile.schedules().getFirst());
        store.reconcile(removed);
        store.recordExecution(TaskExecution.succeeded(
                removed.scheduleId(), null, Instant.EPOCH, Instant.EPOCH));

        var fixture = fixtureWithOneSchedule(store);
        assertThat(store.list()).extracting(ScheduledTaskView::profileName).containsExactly("weather");
        assertThat(store.executionViews(removed.scheduleId())).hasSize(1);
        fixture.scheduler().close();
        assertThat(store.list()).extracting(ScheduledTaskView::profileName).containsExactly("weather");
    }

    @Test
    void identicalRegistrationIsIdempotentAndChangedDefinitionIsRejected() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        AgentScheduler scheduler = scheduler(taskScheduler, new InMemoryScheduledTaskStore());
        Profile original = profile("weather", List.of(
                new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "first")));
        Profile changed = profile("weather", List.of(
                new Profile.ScheduleRef("daily", "0 30 8 * * *", "UTC", "second")));

        scheduler.registerProfile(original);
        scheduler.registerProfile(original);

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        assertThatThrownBy(() -> scheduler.registerProfile(changed))
                .isInstanceOf(IllegalStateException.class);
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        verify(future, never()).cancel(false);
    }

    @Test
    void registrationFailureRollsBackFuturesAndDefinitionsWithoutSensitiveDetails() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        doReturn(firstFuture)
                .doThrow(new IllegalStateException("CANARY-SCHEDULE-DETAIL"))
                .when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        AgentScheduler scheduler = scheduler(taskScheduler, store);
        Profile profile = profile("weather", List.of(
                new Profile.ScheduleRef("first", "0 0 8 * * *", "UTC", "first-message"),
                new Profile.ScheduleRef("second", "0 0 9 * * *", "UTC", "second-message")));

        assertThatThrownBy(() -> scheduler.registerProfile(profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("CANARY-SCHEDULE-DETAIL")
                .hasMessageNotContaining("first-message")
                .hasMessageNotContaining("second-message");

        verify(firstFuture).cancel(false);
        assertThat(store.find(ScheduleId.from("weather", "first"))).isEmpty();
        assertThat(store.find(ScheduleId.from("weather", "second"))).isEmpty();
    }

    @Test
    void callbackCannotRunBeforeWholeProfileRegistrationCommits() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(invocation -> {
                    ((Runnable) invocation.getArgument(0)).run();
                    return firstFuture;
                })
                .thenThrow(new IllegalStateException("second registration failed"));
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        AgentService agents = mock(AgentService.class);
        SessionManager sessions = mock(SessionManager.class);
        AgentScheduler scheduler = new AgentScheduler(
                taskScheduler,
                new ProfileRegistry(),
                agents,
                sessions,
                store,
                Clock.systemUTC());
        Profile profile = profile("weather", List.of(
                new Profile.ScheduleRef("first", "0 0 8 * * *", "UTC", "first-message"),
                new Profile.ScheduleRef("second", "0 0 9 * * *", "UTC", "second-message")));

        assertThatThrownBy(() -> scheduler.registerProfile(profile))
                .isInstanceOf(IllegalStateException.class);

        verify(sessions, never()).getOrCreate(anyString(), anyString(), anyString());
        verify(agents, never()).process(any(Session.class), anyString());
        verify(firstFuture).cancel(false);
    }

    @Test
    void nullFutureIsRegistrationFailureAndDefinitionIsRetired() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        doReturn(null).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        AgentScheduler scheduler = scheduler(taskScheduler, store);
        Profile profile = profile("weather", List.of(
                new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "message")));

        assertThatThrownBy(() -> scheduler.registerProfile(profile))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.find(ScheduleId.from("weather", "daily"))).isEmpty();
    }

    @Test
    void unregisterAndCloseCancelWithoutInterruptAndRetireDefinitions() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        ScheduledFuture<?> third = mock(ScheduledFuture.class);
        doReturn(first, second, third)
                .when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();
        AgentScheduler scheduler = scheduler(taskScheduler, store);
        scheduler.registerProfile(profile("weather", List.of(
                new Profile.ScheduleRef("first", "0 0 8 * * *", "UTC", "first"),
                new Profile.ScheduleRef("second", "0 0 9 * * *", "UTC", "second"))));

        scheduler.unregisterProfile("weather");
        scheduler.unregisterProfile("unknown");

        verify(first).cancel(false);
        verify(second).cancel(false);
        assertThat(store.find(ScheduleId.from("weather", "first"))).isEmpty();
        assertThat(store.find(ScheduleId.from("weather", "second"))).isEmpty();

        scheduler.registerProfile(profile("news", List.of(
                new Profile.ScheduleRef("daily", "0 0 10 * * *", "UTC", "news"))));
        scheduler.close();
        scheduler.close();

        verify(third).cancel(false);
        assertThat(store.find(ScheduleId.from("news", "daily"))).isPresent();
    }

    @Test
    void unregisterAndReregisterReuseProfileLockWhileOldRunIsActive() throws Exception {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> oldFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> newFuture = mock(ScheduledFuture.class);
        doReturn(oldFuture, newFuture)
                .when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        ProfileRegistry profiles = new ProfileRegistry();
        AgentService agents = mock(AgentService.class);
        SessionManager sessions = mock(SessionManager.class);
        when(sessions.getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session("weather"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(agents.process(any(Session.class), anyString())).thenAnswer(invocation -> {
            if (entered.getCount() > 0) {
                entered.countDown();
                release.await(10, TimeUnit.SECONDS);
            }
            return "ok";
        });
        AgentScheduler scheduler = new AgentScheduler(
                taskScheduler,
                profiles,
                agents,
                sessions,
                new InMemoryScheduledTaskStore(),
                Clock.systemUTC());
        Profile weather = profile("weather", List.of(
                new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "message")));
        scheduler.registerProfile(weather);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Trigger.class));
        Runnable oldCallback = captor.getValue();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> running = executor.submit(oldCallback);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            scheduler.unregisterProfile("weather");
            scheduler.registerProfile(weather);
            verify(taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
            Runnable newCallback = captor.getAllValues().get(2);

            newCallback.run();
            verify(agents).process(any(Session.class), anyString());
            release.countDown();
            running.get(10, TimeUnit.SECONDS);

            newCallback.run();
            verify(agents, times(2)).process(any(Session.class), anyString());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        verify(oldFuture).cancel(false);
    }

    @Test
    void unregisteredGenerationCannotRunAfterSameProfileIsRegisteredAgain() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> oldFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> newFuture = mock(ScheduledFuture.class);
        doReturn(oldFuture, newFuture)
                .when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        AgentService agents = mock(AgentService.class);
        SessionManager sessions = mock(SessionManager.class);
        when(sessions.getOrCreate("scheduler", "scheduler", "weather")).thenReturn(session("weather"));
        AgentScheduler scheduler = new AgentScheduler(
                taskScheduler,
                new ProfileRegistry(),
                agents,
                sessions,
                new InMemoryScheduledTaskStore(),
                Clock.systemUTC());
        Profile weather = profile("weather", List.of(
                new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "message")));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        scheduler.registerProfile(weather);
        verify(taskScheduler).schedule(captor.capture(), any(Trigger.class));
        Runnable oldCallback = captor.getValue();
        scheduler.unregisterProfile("weather");
        scheduler.registerProfile(weather);
        verify(taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
        Runnable newCallback = captor.getAllValues().get(2);

        oldCallback.run();
        newCallback.run();

        verify(agents).process(any(Session.class), eq("message"));
    }

    @Test
    void reorderedDefinitionsAreAnIdempotentRegistration() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        doReturn(first, second).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        AgentScheduler scheduler = scheduler(taskScheduler, new InMemoryScheduledTaskStore());
        Profile original = profile("weather", List.of(
                new Profile.ScheduleRef("morning", "0 0 8 * * *", "UTC", "morning"),
                new Profile.ScheduleRef("evening", "0 0 18 * * *", "UTC", "evening")));
        Profile reordered = profile("weather", List.of(
                new Profile.ScheduleRef("evening", "0 0 18 * * *", "UTC", "evening"),
                new Profile.ScheduleRef("morning", "0 0 8 * * *", "UTC", "morning")));

        scheduler.registerProfile(original);
        scheduler.registerProfile(reordered);

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void closeIsTerminalForRegistration() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        AgentScheduler scheduler = scheduler(taskScheduler, new InMemoryScheduledTaskStore());
        scheduler.close();

        assertThatThrownBy(() -> scheduler.registerProfile(profile("weather", List.of(
                        new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "message")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("关闭");

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    private static AgentScheduler scheduler(TaskScheduler taskScheduler, ScheduledTaskStore store) {
        return new AgentScheduler(
                taskScheduler,
                new ProfileRegistry(),
                mock(AgentService.class),
                mock(SessionManager.class),
                store,
                Clock.systemUTC());
    }

    private static Fixture fixtureWithOneSchedule(ScheduledTaskStore store) {
        return fixtureWithOneSchedule(store, Clock.systemUTC());
    }

    private static Fixture fixtureWithOneSchedule(ScheduledTaskStore store, Clock clock) {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        ProfileRegistry profiles = new ProfileRegistry();
        profiles.register(profile("weather", List.of(
                new Profile.ScheduleRef("daily", "0 0 8 * * *", "UTC", "message"))));
        AgentService agents = mock(AgentService.class);
        SessionManager sessions = mock(SessionManager.class);
        AgentScheduler scheduler = new AgentScheduler(
                taskScheduler, profiles, agents, sessions, store, clock);
        scheduler.registerAll();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Trigger.class));
        return new Fixture(scheduler, captor.getValue(), agents, sessions,
                store instanceof InMemoryScheduledTaskStore memory ? memory : null);
    }

    private static Session session(String profileName) {
        return new Session("scheduler", "scheduler", profileName);
    }

    private record Fixture(
            AgentScheduler scheduler,
            Runnable callback,
            AgentService agents,
            SessionManager sessions,
            InMemoryScheduledTaskStore memoryStore) {
    }

    private static Profile profile(String name, List<Profile.ScheduleRef> schedules) {
        return new Profile(
                name,
                "description",
                new Profile.Identity(name, "prompt"),
                new Profile.ProviderRef("provider", "model", null),
                List.of(),
                List.of(),
                List.of(),
                schedules,
                List.of(),
                new Profile.Settings(8, 20),
                Path.of(".", name, "AGENT.md"));
    }
}
