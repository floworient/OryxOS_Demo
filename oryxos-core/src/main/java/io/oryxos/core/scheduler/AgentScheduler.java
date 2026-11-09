package io.oryxos.core.scheduler;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

public final class AgentScheduler implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentScheduler.class);

    private final TaskScheduler taskScheduler;

    private final ProfileRegistry profiles;

    private final AgentService agents;

    private final SessionManager sessions;

    private final ScheduledTaskStore store;

    private final Clock clock;

    private final Duration timeout;

    private final ExecutorService workers;

    private final Map<String, List<ScheduledTaskDefinition>> definitionsByProfile = new LinkedHashMap<>();

    private final Map<String, List<ScheduledFuture<?>>> futuresByProfile = new LinkedHashMap<>();

    private final Map<String, RegistrationGeneration> generationsByProfile = new LinkedHashMap<>();

    private final Map<String, ReentrantLock> profileLocks = new ConcurrentHashMap<>();

    private boolean closed;

    public AgentScheduler(
            TaskScheduler taskScheduler,
            ProfileRegistry profiles,
            AgentService agents,
            SessionManager sessions,
            ScheduledTaskStore store,
            Clock clock) {
        this(taskScheduler, profiles, agents, sessions, store, clock, Duration.ofSeconds(60));
    }

    public AgentScheduler(
            TaskScheduler taskScheduler,
            ProfileRegistry profiles,
            AgentService agents,
            SessionManager sessions,
            ScheduledTaskStore store,
            Clock clock,
            Duration timeout) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("定时任务超时必须大于零");
        }
        this.workers = Executors.newVirtualThreadPerTaskExecutor();
    }

    public synchronized void registerAll() {
        ensureOpen();
        profiles.all().forEach(this::registerProfile);
        var activeIds = definitionsByProfile.values().stream().flatMap(List::stream)
                .map(ScheduledTaskDefinition::scheduleId).collect(java.util.stream.Collectors.toSet());
        store.list().stream().map(ScheduledTaskView::scheduleId)
                .filter(id -> !activeIds.contains(id)).forEach(store::retire);
    }

    public synchronized void registerProfile(Profile profile) {
        ensureOpen();
        Objects.requireNonNull(profile, "profile");
        List<RegistrationCandidate> candidates = profile.schedules().stream()
                .map(schedule -> ScheduledTaskDefinition.from(profile, schedule))
                .map(definition -> new RegistrationCandidate(
                        definition,
                        new CronTrigger(definition.cron(), definition.zoneId())))
                .sorted(Comparator.comparing(candidate -> candidate.definition().scheduleId().value()))
                .toList();
        List<ScheduledTaskDefinition> definitions = candidates.stream()
                .map(RegistrationCandidate::definition)
                .toList();
        List<ScheduledTaskDefinition> registered = definitionsByProfile.get(profile.name());
        if (registered != null) {
            if (registered.equals(definitions)) {
                return;
            }
            throw new IllegalStateException("Agent 已注册不同的定时任务定义: " + profile.name());
        }
        if (definitions.isEmpty()) {
            definitionsByProfile.put(profile.name(), definitions);
            futuresByProfile.put(profile.name(), List.of());
            RegistrationGeneration generation = new RegistrationGeneration();
            generation.activate();
            generationsByProfile.put(profile.name(), generation);
            return;
        }

        ReentrantLock lock = profileLocks.computeIfAbsent(profile.name(), ignored -> new ReentrantLock());
        RegistrationGeneration generation = new RegistrationGeneration();
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        try {
            for (RegistrationCandidate candidate : candidates) {
                ScheduledTaskDefinition definition = candidate.definition();
                store.reconcile(definition);
                store.updateNextRun(definition.scheduleId(),
                        candidate.trigger().nextExecution(new SimpleTriggerContext(clock)));
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> runCron(definition, lock, generation), trackingTrigger(definition, generation));
                if (future == null) {
                    throw new IllegalStateException("TaskScheduler 返回 null future");
                }
                futures.add(future);
            }
        } catch (RuntimeException failure) {
            generation.deactivate();
            futures.forEach(this::cancelSafely);
            definitions.forEach(this::retireSafely);
            LOGGER.error(
                    "定时任务注册失败: profileName={}, errorType={}",
                    profile.name(),
                    TaskExecution.safeErrorType(failure));
            throw new IllegalStateException(
                    "定时任务注册失败: profileName=" + profile.name()
                            + ", errorType=" + TaskExecution.safeErrorType(failure));
        }
        definitionsByProfile.put(profile.name(), List.copyOf(definitions));
        futuresByProfile.put(profile.name(), List.copyOf(futures));
        generationsByProfile.put(profile.name(), generation);
        generation.activate();
    }

    public Optional<TaskExecutionView> runNow(ScheduleId scheduleId) throws TimeoutException {
        ScheduledTaskDefinition definition;
        RegistrationGeneration generation;
        ReentrantLock lock;
        synchronized (this) {
            ensureOpen();
            definition = definitionsByProfile.values().stream().flatMap(List::stream)
                    .filter(candidate -> candidate.scheduleId().equals(scheduleId))
                    .findFirst().orElseThrow(() -> new NoSuchElementException("定时任务不存在"));
            generation = generationsByProfile.get(definition.profileName());
            lock = profileLocks.get(definition.profileName());
        }
        return awaitRun(definition, lock, generation, true);
    }

    private void runCron(ScheduledTaskDefinition definition, ReentrantLock lock,
            RegistrationGeneration generation) {
        if (!generation.isActive()) {
            return;
        }
        try {
            awaitRun(definition, lock, generation, false);
        } catch (TimeoutException ignored) {
            // 截止时间已记入本次历史，CronTrigger 可正常计算下一次。
        } catch (RuntimeException failure) {
            LOGGER.warn("定时任务触发失败: scheduleId={}, errorType={}",
                    definition.scheduleId().value(), TaskExecution.safeErrorType(failure));
        }
    }

    private Optional<TaskExecutionView> awaitRun(ScheduledTaskDefinition definition, ReentrantLock lock,
            RegistrationGeneration generation, boolean manual) throws TimeoutException {
        var attempt = new ExecutionAttempt();
        Future<Optional<TaskExecutionView>> future =
                workers.submit(() -> run(definition, lock, generation, manual, attempt));
        try {
            try {
                return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException deadline) {
                if (!cancelAttempt(definition, manual, attempt, future, deadline)) {
                    if (future.isDone()) {
                        return future.get();
                    }
                    throw new IllegalStateException("定时任务状态写入超过等待时限");
                }
                throw deadline;
            }
        } catch (InterruptedException interrupted) {
            try {
                cancelAttempt(definition, manual, attempt, future, interrupted);
            } finally {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("定时任务等待已中断", interrupted);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("定时任务执行失败", exception.getCause());
        }
    }

    private boolean cancelAttempt(ScheduledTaskDefinition definition, boolean manual, ExecutionAttempt attempt,
            Future<?> future, Exception failure) {
        synchronized (attempt) {
            if (attempt.finished) {
                return false;
            }
            attempt.cancelled = true;
            future.cancel(true);
            if (attempt.startedAt != null) {
                record(definition, TaskExecution.failed(definition.scheduleId(), attempt.sessionId,
                        attempt.startedAt, finishedAt(attempt.startedAt), failure), manual);
            }
            return true;
        }
    }

    private CronTrigger trackingTrigger(ScheduledTaskDefinition definition, RegistrationGeneration generation) {
        return new CronTrigger(definition.cron(), definition.zoneId()) {
            @Override
            public Instant nextExecution(TriggerContext triggerContext) {
                Instant next = super.nextExecution(triggerContext);
                generation.updateIfCurrent(() -> {
                    try {
                        store.updateNextRun(definition.scheduleId(), next);
                    } catch (RuntimeException failure) {
                        LOGGER.warn("定时任务下次触发时间写入失败: scheduleId={}, errorType={}",
                                definition.scheduleId().value(), TaskExecution.safeErrorType(failure));
                    }
                });
                return next;
            }
        };
    }

    private Optional<TaskExecutionView> run(
            ScheduledTaskDefinition definition,
            ReentrantLock lock,
            RegistrationGeneration generation,
            boolean manual,
            ExecutionAttempt attempt) {
        if (!generation.isActive()) {
            if (manual) {
                throw new NoSuchElementException("定时任务不存在");
            }
            return Optional.empty();
        }
        if (!lock.tryLock()) {
            LOGGER.info(
                    "跳过重叠定时任务: scheduleId={}, profileName={}",
                    definition.scheduleId().value(),
                    definition.profileName());
            return Optional.empty();
        }
        try {
            if (!generation.isActive()) {
                if (manual) {
                    throw new NoSuchElementException("定时任务不存在");
                }
                return Optional.empty();
            }
            if (!manual && !store.isEnabled(definition.scheduleId())) {
                return Optional.empty();
            }
            Instant startedAt;
            synchronized (attempt) {
                if (attempt.cancelled) {
                    return Optional.empty();
                }
                startedAt = clock.instant();
                attempt.startedAt = startedAt;
            }
            String sessionId = null;
            TaskExecution execution;
            try {
                Session session = sessions.getOrCreate("scheduler", "scheduler", definition.profileName());
                sessionId = session.id();
                synchronized (attempt) {
                    if (attempt.cancelled) {
                        return Optional.empty();
                    }
                    attempt.sessionId = sessionId;
                }
                LOGGER.info("开始定时任务: scheduleId={}, profileName={}, sessionId={}",
                        definition.scheduleId().value(), definition.profileName(), sessionId);
                AgentService.withCancellation(() -> attempt.cancelled,
                        () -> agents.process(session, definition.message()));
                execution = TaskExecution.succeeded(
                        definition.scheduleId(), sessionId, startedAt, finishedAt(startedAt));
            } catch (RuntimeException failure) {
                execution = TaskExecution.failed(
                        definition.scheduleId(), sessionId, startedAt, finishedAt(startedAt), failure);
            }
            synchronized (attempt) {
                if (attempt.cancelled) {
                    return Optional.empty();
                }
                attempt.finished = true;
            }
            record(definition, execution, manual);
            return Optional.of(TaskExecutionView.from(execution));
        } finally {
            lock.unlock();
        }
    }

    private void record(ScheduledTaskDefinition definition, TaskExecution execution, boolean manual) {
        if (execution.success()) {
            LOGGER.info("定时任务执行完成: scheduleId={}, profileName={}, sessionId={}",
                    definition.scheduleId().value(), definition.profileName(), execution.sessionId());
        } else {
            LOGGER.warn("定时任务执行失败: scheduleId={}, profileName={}, sessionId={}, errorType={}",
                    definition.scheduleId().value(), definition.profileName(),
                    execution.sessionId(), execution.errorType());
        }
        try {
            store.recordExecution(execution);
        } catch (RuntimeException failure) {
            LOGGER.warn("定时任务执行记录失败: scheduleId={}, sessionId={}, errorType={}",
                    definition.scheduleId().value(), execution.sessionId(), TaskExecution.safeErrorType(failure));
            if (manual) {
                throw failure;
            }
        }
    }

    public synchronized void unregisterProfile(String profileName) {
        Objects.requireNonNull(profileName, "profileName");
        RegistrationGeneration generation = generationsByProfile.remove(profileName);
        if (generation != null) {
            generation.deactivate();
        }
        List<ScheduledFuture<?>> futures = futuresByProfile.remove(profileName);
        List<ScheduledTaskDefinition> definitions = definitionsByProfile.remove(profileName);
        if (futures != null) {
            futures.forEach(this::cancelSafely);
        }
        if (definitions != null) {
            definitions.forEach(this::retireSafely);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            generationsByProfile.values().forEach(RegistrationGeneration::deactivate);
            futuresByProfile.values().forEach(futures -> futures.forEach(this::cancelSafely));
            generationsByProfile.clear();
            futuresByProfile.clear();
            definitionsByProfile.clear();
            workers.shutdown();
        }
        try {
            if (!workers.awaitTermination(timeout.plusSeconds(1).toNanos(), TimeUnit.NANOSECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Instant finishedAt(Instant startedAt) {
        Instant current = clock.instant();
        return current.isBefore(startedAt) ? startedAt : current;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("定时调度器已关闭");
        }
    }

    private void cancelSafely(ScheduledFuture<?> future) {
        try {
            future.cancel(false);
        } catch (RuntimeException failure) {
            LOGGER.warn("取消定时任务失败: errorType={}", TaskExecution.safeErrorType(failure));
        }
    }

    private void retireSafely(ScheduledTaskDefinition definition) {
        try {
            store.retire(definition.scheduleId());
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "退役定时任务定义失败: scheduleId={}, profileName={}, errorType={}",
                    definition.scheduleId().value(),
                    definition.profileName(),
                    TaskExecution.safeErrorType(failure));
        }
    }

    private static final class RegistrationGeneration {

        private volatile boolean active;

        private boolean acceptingUpdates = true;

        private void activate() {
            active = true;
        }

        private synchronized void deactivate() {
            active = false;
            acceptingUpdates = false;
        }

        private synchronized void updateIfCurrent(Runnable update) {
            if (acceptingUpdates) {
                update.run();
            }
        }

        private boolean isActive() {
            return active;
        }
    }

    private static final class ExecutionAttempt {

        private Instant startedAt;

        private String sessionId;

        private volatile boolean cancelled;

        private boolean finished;
    }

    private record RegistrationCandidate(ScheduledTaskDefinition definition, CronTrigger trigger) {
    }
}
