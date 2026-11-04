package io.oryxos.core.scheduler;

import java.util.List;
import java.util.Optional;

public interface ScheduledTaskStore {

    ScheduledTaskDefinition reconcile(ScheduledTaskDefinition definition);

    void retire(ScheduleId scheduleId);

    Optional<ScheduledTaskDefinition> find(ScheduleId scheduleId);

    void recordExecution(TaskExecution execution);

    List<TaskExecution> executions(ScheduleId scheduleId);
}
