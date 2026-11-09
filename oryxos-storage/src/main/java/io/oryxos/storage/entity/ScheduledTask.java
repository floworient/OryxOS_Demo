package io.oryxos.storage.entity;

import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskDefinition;
import io.oryxos.core.scheduler.ScheduledTaskView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;

@Entity
@Table(name = "scheduled_tasks")
public class ScheduledTask {

    @Id
    @Column(name = "schedule_id", length = 36)
    private String scheduleId;

    @Column(nullable = false)
    private String profileName;

    @Column(nullable = false)
    private String scheduleKey;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String cron;

    @Column(nullable = false)
    private String zone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean retired;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(length = 16)
    private String lastStatus;

    @Column(nullable = false)
    private long runCount;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ScheduledTask() {
    }

    public ScheduledTaskDefinition toDefinition() {
        return new ScheduledTaskDefinition(
                new ScheduleId(scheduleId), profileName, scheduleKey, cron, ZoneId.of(zone), message);
    }

    public ScheduledTaskView toView() {
        return new ScheduledTaskView(new ScheduleId(scheduleId), profileName, scheduleKey,
                displayName, cron, zone, enabled, nextRunAt, lastRunAt, lastStatus, runCount);
    }
}
