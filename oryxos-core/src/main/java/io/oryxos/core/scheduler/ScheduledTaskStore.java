package io.oryxos.core.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduledTaskStore {

    ScheduledTaskDefinition reconcile(ScheduledTaskDefinition definition);

    void retire(ScheduleId scheduleId);

    Optional<ScheduledTaskDefinition> find(ScheduleId scheduleId);

    void recordExecution(TaskExecution execution);

    List<TaskExecution> executions(ScheduleId scheduleId);

    boolean isEnabled(ScheduleId scheduleId);

    Optional<ScheduledTaskView> setEnabled(ScheduleId scheduleId, boolean enabled);

    List<ScheduledTaskView> list();

    boolean exists(ScheduleId scheduleId);

    List<TaskExecutionView> executionViews(ScheduleId scheduleId);

    void updateNextRun(ScheduleId scheduleId, Instant nextRunAt);
}
