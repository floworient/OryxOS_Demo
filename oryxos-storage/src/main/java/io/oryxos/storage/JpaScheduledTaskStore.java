package io.oryxos.storage;

import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskDefinition;
import io.oryxos.core.scheduler.ScheduledTaskStore;
import io.oryxos.core.scheduler.ScheduledTaskView;
import io.oryxos.core.scheduler.TaskExecution;
import io.oryxos.core.scheduler.TaskExecutionView;
import io.oryxos.storage.entity.ScheduledTask;
import io.oryxos.storage.repository.ScheduledTaskRepository;
import io.oryxos.storage.repository.TaskExecutionRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaScheduledTaskStore implements ScheduledTaskStore {

    private final ScheduledTaskRepository tasks;

    private final TaskExecutionRepository history;

    JpaScheduledTaskStore(ScheduledTaskRepository tasks, TaskExecutionRepository history) {
        this.tasks = tasks;
        this.history = history;
    }

    @Override
    @Transactional
    public ScheduledTaskDefinition reconcile(ScheduledTaskDefinition definition) {
        tasks.reconcile(definition.scheduleId().value(), definition.profileName(), definition.scheduleKey(),
                definition.cron(), definition.zoneId().getId(), definition.message(), Instant.now());
        return tasks.findByScheduleIdAndRetiredFalse(definition.scheduleId().value())
                .map(ScheduledTask::toDefinition).orElseThrow();
    }

    @Override
    @Transactional
    public void retire(ScheduleId scheduleId) {
        tasks.retire(scheduleId.value(), Instant.now());
    }

    @Override
    public Optional<ScheduledTaskDefinition> find(ScheduleId scheduleId) {
        return tasks.findByScheduleIdAndRetiredFalse(scheduleId.value()).map(ScheduledTask::toDefinition);
    }

    @Override
    @Transactional
    public void recordExecution(TaskExecution execution) {
        var view = TaskExecutionView.from(execution);
        history.saveAndFlush(new io.oryxos.storage.entity.TaskExecution(view));
        if (tasks.complete(view.scheduleId().value(), view.startedAt(),
                view.success() ? "success" : "failed", Instant.now()) != 1) {
            throw new NoSuchElementException("定时任务不存在");
        }
    }

    @Override
    public List<TaskExecution> executions(ScheduleId scheduleId) {
        return history.findAllByScheduleIdOrderByIdAsc(scheduleId.value()).stream()
                .map(io.oryxos.storage.entity.TaskExecution::toView)
                .map(JpaScheduledTaskStore::toExecution)
                .toList();
    }

    @Override
    public boolean isEnabled(ScheduleId scheduleId) {
        return tasks.existsByScheduleIdAndRetiredFalseAndEnabledTrue(scheduleId.value());
    }

    @Override
    @Transactional
    public Optional<ScheduledTaskView> setEnabled(ScheduleId scheduleId, boolean enabled) {
        if (tasks.setEnabled(scheduleId.value(), enabled, Instant.now()) == 0) {
            return Optional.empty();
        }
        return tasks.findByScheduleIdAndRetiredFalse(scheduleId.value()).map(ScheduledTask::toView);
    }

    @Override
    public List<ScheduledTaskView> list() {
        return tasks.findAllByRetiredFalseOrderByProfileNameAscScheduleKeyAsc().stream()
                .map(ScheduledTask::toView).toList();
    }

    @Override
    public boolean exists(ScheduleId scheduleId) {
        return tasks.existsById(scheduleId.value());
    }

    @Override
    public List<TaskExecutionView> executionViews(ScheduleId scheduleId) {
        return history.findAllByScheduleIdOrderByStartedAtDescIdDesc(scheduleId.value()).stream()
                .map(io.oryxos.storage.entity.TaskExecution::toView).toList();
    }

    @Override
    @Transactional
    public void updateNextRun(ScheduleId scheduleId, Instant nextRunAt) {
        tasks.updateNextRun(scheduleId.value(), nextRunAt, Instant.now());
    }

    private static TaskExecution toExecution(TaskExecutionView view) {
        String errorType = view.success() ? null : switch (view.errorMessage()) {
            case "Agent 调用超时" -> "TimeoutException";
            case "模型调用失败" -> "ProviderException";
            default -> "IllegalStateException";
        };
        return new TaskExecution(view.scheduleId(), view.sessionId(), view.startedAt(),
                view.startedAt().plusMillis(view.durationMs()), view.success(), errorType);
    }
}
