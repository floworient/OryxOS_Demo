package io.oryxos.core.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryScheduledTaskStore implements ScheduledTaskStore {

    private final ConcurrentMap<ScheduleId, ScheduledTaskDefinition> definitions = new ConcurrentHashMap<>();

    private final ConcurrentMap<ScheduleId, CopyOnWriteArrayList<TaskExecution>> executionHistory =
            new ConcurrentHashMap<>();

    @Override
    public ScheduledTaskDefinition reconcile(ScheduledTaskDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        definitions.put(definition.scheduleId(), definition);
        return definition;
    }

    @Override
    public void retire(ScheduleId scheduleId) {
        definitions.remove(Objects.requireNonNull(scheduleId, "scheduleId"));
    }

    @Override
    public Optional<ScheduledTaskDefinition> find(ScheduleId scheduleId) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(scheduleId, "scheduleId")));
    }

    @Override
    public void recordExecution(TaskExecution execution) {
        Objects.requireNonNull(execution, "execution");
        executionHistory
                .computeIfAbsent(execution.scheduleId(), ignored -> new CopyOnWriteArrayList<>())
                .add(execution);
    }

    @Override
    public List<TaskExecution> executions(ScheduleId scheduleId) {
        CopyOnWriteArrayList<TaskExecution> executions =
                executionHistory.get(Objects.requireNonNull(scheduleId, "scheduleId"));
        return executions == null ? List.of() : List.copyOf(executions);
    }
}
