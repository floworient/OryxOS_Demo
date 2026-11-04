package io.oryxos.core.scheduler;

import java.time.Instant;
import java.util.Objects;

public record TaskExecution(
        ScheduleId scheduleId,
        String sessionId,
        Instant startedAt,
        Instant finishedAt,
        boolean success,
        String errorType) {

    public TaskExecution {
        Objects.requireNonNull(scheduleId, "scheduleId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt 不能早于 startedAt");
        }
        if (success && errorType != null) {
            throw new IllegalArgumentException("成功执行不能包含 errorType");
        }
        if (!success && (errorType == null || errorType.isBlank())) {
            throw new IllegalArgumentException("失败执行必须包含 errorType");
        }
    }

    public static TaskExecution succeeded(
            ScheduleId scheduleId, String sessionId, Instant startedAt, Instant finishedAt) {
        return new TaskExecution(scheduleId, sessionId, startedAt, finishedAt, true, null);
    }

    public static TaskExecution failed(
            ScheduleId scheduleId,
            String sessionId,
            Instant startedAt,
            Instant finishedAt,
            Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        return new TaskExecution(
                scheduleId,
                sessionId,
                startedAt,
                finishedAt,
                false,
                safeErrorType(failure));
    }

    static String safeErrorType(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        Class<?> type = failure.getClass();
        while (type != null && type.getSimpleName().isBlank()) {
            type = type.getSuperclass();
        }
        return type == null ? "Throwable" : type.getSimpleName();
    }
}
