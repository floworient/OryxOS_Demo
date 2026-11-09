package io.oryxos.core.scheduler;

import java.time.Instant;

/** 已登记任务的运行状态。 */
public record ScheduledTaskView(
        ScheduleId scheduleId,
        String profileName,
        String scheduleKey,
        String displayName,
        String cron,
        String zone,
        boolean enabled,
        Instant nextRunAt,
        Instant lastRunAt,
        String lastStatus,
        long runCount) {
}
