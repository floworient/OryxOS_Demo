package io.oryxos.core.scheduler;

import io.oryxos.core.profile.Profile;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.scheduling.support.CronExpression;

public record ScheduledTaskDefinition(
        ScheduleId scheduleId,
        String profileName,
        String scheduleKey,
        String cron,
        ZoneId zoneId,
        String message) {

    public ScheduledTaskDefinition {
        Objects.requireNonNull(scheduleId, "scheduleId");
        requireText(profileName, "profileName");
        requireText(scheduleKey, "scheduleKey");
        requireText(cron, "cron");
        Objects.requireNonNull(zoneId, "zoneId");
        requireText(message, "message");
        if (!scheduleId.equals(ScheduleId.from(profileName, scheduleKey))) {
            throw new IllegalArgumentException("scheduleId 与 profileName/scheduleKey 不一致");
        }
        CronExpression.parse(cron);
    }

    public static ScheduledTaskDefinition from(Profile profile, Profile.ScheduleRef schedule) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(schedule, "schedule");
        return new ScheduledTaskDefinition(
                ScheduleId.from(profile.name(), schedule.key()),
                profile.name(),
                schedule.key(),
                schedule.cron(),
                ZoneId.of(schedule.zone()),
                schedule.message());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
