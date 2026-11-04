package io.oryxos.core.scheduler;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

public final class AgentScheduler implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentScheduler.class);

    private final TaskScheduler taskScheduler;

    private final ProfileRegistry profiles;

    private final AgentService agents;

    private final SessionManager sessions;

    private final ScheduledTaskStore store;

    private final Clock clock;

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
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void registerAll() {
        ensureOpen();
        profiles.all().forEach(this::registerProfile);
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
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> run(definition, lock, generation), candidate.trigger());
                if (future == null) {
                    throw new IllegalStateException("TaskScheduler 返回 null future");
                }
                futures.add(future);
            }
        } catch (RuntimeException failure) {
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

    private void run(
            ScheduledTaskDefinition definition,
            ReentrantLock lock,
            RegistrationGeneration generation) {
        if (!generation.isActive()) {
            return;
        }
        if (!lock.tryLock()) {
            LOGGER.info(
                    "跳过重叠定时任务: scheduleId={}, profileName={}",
                    definition.scheduleId().value(),
                    definition.profileName());
            return;
        }
        try {
            if (!generation.isActive()) {
                return;
            }
            Instant startedAt = clock.instant();
            String sessionId = null;
            TaskExecution execution;
            try {
                Session session = sessions.getOrCreate("scheduler", "scheduler", definition.profileName());
                sessionId = session.id();
                agents.process(session, definition.message());
                execution = TaskExecution.succeeded(
                        definition.scheduleId(), sessionId, startedAt, finishedAt(startedAt));
            } catch (RuntimeException failure) {
                execution = TaskExecution.failed(
                        definition.scheduleId(), sessionId, startedAt, finishedAt(startedAt), failure);
                LOGGER.warn(
                        "定时任务执行失败: scheduleId={}, profileName={}, errorType={}",
                        definition.scheduleId().value(),
                        definition.profileName(),
                        TaskExecution.safeErrorType(failure));
            }
            try {
                store.recordExecution(execution);
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "定时任务执行记录失败: scheduleId={}, profileName={}, errorType={}",
                        definition.scheduleId().value(),
                        definition.profileName(),
                        TaskExecution.safeErrorType(failure));
            }
        } finally {
            lock.unlock();
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
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List.copyOf(definitionsByProfile.keySet()).forEach(this::unregisterProfile);
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

        private void activate() {
            active = true;
        }

        private void deactivate() {
            active = false;
        }

        private boolean isActive() {
            return active;
        }
    }

    private record RegistrationCandidate(ScheduledTaskDefinition definition, CronTrigger trigger) {
    }
}
