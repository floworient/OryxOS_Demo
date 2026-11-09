package io.oryxos.storage.entity;

import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.TaskExecutionView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_executions")
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long id;

    @Column(length = 36)
    private String scheduleId;

    private String sessionId;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private boolean success;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private long durationMs;

    protected TaskExecution() {
    }

    public TaskExecution(TaskExecutionView execution) {
        scheduleId = execution.scheduleId().value();
        sessionId = execution.sessionId();
        startedAt = execution.startedAt();
        success = execution.success();
        errorMessage = execution.errorMessage();
        durationMs = execution.durationMs();
    }

    public TaskExecutionView toView() {
        return new TaskExecutionView(new ScheduleId(scheduleId), sessionId, startedAt,
                success, errorMessage, durationMs);
    }
}
