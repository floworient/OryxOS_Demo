package io.oryxos.core.scheduler;

import java.time.Duration;
import java.time.Instant;

/** 对外可见的单次执行结果。错误信息来自安全文案。 */
public record TaskExecutionView(
        ScheduleId scheduleId,
        String sessionId,
        Instant startedAt,
        boolean success,
        String errorMessage,
        long durationMs) {

    public static TaskExecutionView from(TaskExecution execution) {
        String message = execution.success() ? null : switch (execution.errorType()) {
            case "TimeoutException" -> "Agent 调用超时";
            case "ProviderException" -> "模型调用失败";
            default -> execution.sessionId() == null ? "无法取得定时会话" : "Agent 执行失败";
        };
        return new TaskExecutionView(
                execution.scheduleId(), execution.sessionId(), execution.startedAt(),
                execution.success(), message,
                Duration.between(execution.startedAt(), execution.finishedAt()).toMillis());
    }
}
