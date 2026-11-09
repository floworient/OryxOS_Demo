package io.oryxos.core.scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryScheduledTaskStore implements ScheduledTaskStore {

    private final Map<ScheduleId, State> tasks = new HashMap<>();

    private final Map<ScheduleId, List<TaskExecution>> executionHistory = new HashMap<>();

    @Override
    public synchronized ScheduledTaskDefinition reconcile(ScheduledTaskDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        State state = tasks.computeIfAbsent(definition.scheduleId(), ignored -> new State());
        state.definition = definition;
        state.retired = false;
        return definition;
    }

    @Override
    public synchronized void retire(ScheduleId scheduleId) {
        active(scheduleId).ifPresent(state -> {
            state.retired = true;
            state.nextRunAt = null;
        });
    }

    @Override
    public synchronized Optional<ScheduledTaskDefinition> find(ScheduleId scheduleId) {
        return active(scheduleId).map(state -> state.definition);
    }

    @Override
    public synchronized void recordExecution(TaskExecution execution) {
        Objects.requireNonNull(execution, "execution");
        executionHistory
                .computeIfAbsent(execution.scheduleId(), ignored -> new ArrayList<>())
                .add(execution);
    }

    @Override
    public synchronized List<TaskExecution> executions(ScheduleId scheduleId) {
        Objects.requireNonNull(scheduleId, "scheduleId");
        return List.copyOf(executionHistory.getOrDefault(scheduleId, List.of()));
    }

    @Override
    public synchronized boolean isEnabled(ScheduleId scheduleId) {
        return active(scheduleId).map(state -> state.enabled).orElse(false);
    }

    @Override
    public synchronized Optional<ScheduledTaskView> setEnabled(ScheduleId scheduleId, boolean enabled) {
        return active(scheduleId).map(state -> {
            state.enabled = enabled;
            return view(state);
        });
    }

    @Override
    public synchronized List<ScheduledTaskView> list() {
        return tasks.values().stream()
                .filter(state -> !state.retired)
                .map(this::view)
                .sorted(Comparator.comparing(ScheduledTaskView::profileName)
                        .thenComparing(ScheduledTaskView::scheduleKey))
                .toList();
    }

    @Override
    public synchronized boolean exists(ScheduleId scheduleId) {
        return tasks.containsKey(Objects.requireNonNull(scheduleId, "scheduleId"));
    }

    @Override
    public synchronized List<TaskExecutionView> executionViews(ScheduleId scheduleId) {
        return executions(scheduleId).reversed().stream()
                .sorted(Comparator.comparing(TaskExecution::startedAt).reversed())
                .map(TaskExecutionView::from)
                .toList();
    }

    @Override
    public synchronized void updateNextRun(ScheduleId scheduleId, Instant nextRunAt) {
        active(scheduleId).ifPresent(state -> state.nextRunAt = nextRunAt);
    }

    private Optional<State> active(ScheduleId scheduleId) {
        return Optional.ofNullable(tasks.get(Objects.requireNonNull(scheduleId, "scheduleId")))
                .filter(state -> !state.retired);
    }

    private ScheduledTaskView view(State state) {
        var definition = state.definition;
        var history = executionHistory.getOrDefault(definition.scheduleId(), List.of());
        var last = history.isEmpty() ? null : history.getLast();
        return new ScheduledTaskView(
                definition.scheduleId(), definition.profileName(), definition.scheduleKey(),
                definition.scheduleKey(), definition.cron(), definition.zoneId().getId(),
                state.enabled, state.nextRunAt, last == null ? null : last.startedAt(),
                last == null ? null : last.success() ? "success" : "failed", history.size());
    }

    private static final class State {

        private ScheduledTaskDefinition definition;

        private boolean enabled = true;

        private boolean retired;

        private Instant nextRunAt;
    }
}
