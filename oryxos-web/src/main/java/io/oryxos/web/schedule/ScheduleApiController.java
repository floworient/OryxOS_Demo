package io.oryxos.web.schedule;

import io.oryxos.core.scheduler.AgentScheduler;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskStore;
import io.oryxos.core.scheduler.ScheduledTaskView;
import io.oryxos.core.scheduler.TaskExecutionView;
import io.oryxos.web.api.AgentCallTimeoutException;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/json")
public class ScheduleApiController {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleApiController.class);

    private static final String V1 = "/api/v1/schedules";

    private static final String V2 = "/api/v2/schedules";

    private final AgentScheduler scheduler;

    private final ScheduledTaskStore store;

    ScheduleApiController(AgentScheduler scheduler, ScheduledTaskStore store) {
        this.scheduler = scheduler;
        this.store = store;
    }

    @GetMapping({V1, V2})
    public ApiResponse<List<ScheduleResponse>> list() {
        return ApiResponse.success(store.list().stream().map(ScheduleResponse::from).toList());
    }

    @GetMapping(V2 + "/{scheduleId}/executions")
    public ApiResponse<List<ExecutionResponse>> history(@PathVariable("scheduleId") String scheduleId) {
        return historyById(parseId(scheduleId));
    }

    @GetMapping(V1 + "/{id}/executions")
    public ApiResponse<List<ExecutionResponse>> legacyHistory(@PathVariable("id") String key) {
        return historyById(legacyId(key));
    }

    @PostMapping(V2 + "/{scheduleId}/run")
    public ApiResponse<ExecutionResponse> run(@PathVariable("scheduleId") String scheduleId) {
        return runById(parseId(scheduleId));
    }

    @PostMapping("/api/v2/agents/{profileName}/schedules/{key}/run")
    public ApiResponse<ExecutionResponse> runForAgent(
            @PathVariable("profileName") String profileName, @PathVariable("key") String key) {
        return runById(ScheduleId.from(profileName, key));
    }

    @PostMapping(V1 + "/{id}/run")
    public ApiResponse<ExecutionResponse> legacyRun(@PathVariable("id") String key) {
        return runById(legacyId(key));
    }

    @PutMapping(V2 + "/{scheduleId}")
    public ApiResponse<ScheduleResponse> update(@PathVariable("scheduleId") String scheduleId,
            @RequestBody UpdateScheduleRequest request) {
        return updateById(parseId(scheduleId), request);
    }

    @PutMapping(V1 + "/{id}")
    public ApiResponse<ScheduleResponse> legacyUpdate(@PathVariable("id") String key,
            @RequestBody UpdateScheduleRequest request) {
        return updateById(legacyId(key), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> invalidBody() {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请求体必须是合法 JSON"));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiResponse<Void>> safeError(RuntimeException failure) {
        if (failure instanceof ApiException api) {
            return ResponseEntity.status(api.status()).body(ApiResponse.error(api.code(), api.getMessage()));
        }
        if (failure instanceof AgentCallTimeoutException) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(ApiResponse.error("AGENT_TIMEOUT", "Agent 调用超时"));
        }
        if (failure instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "调度参数无效"));
        }
        LOG.warn("定时任务管理失败: errorType={}", failure.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_ERROR", "定时任务操作失败"));
    }

    private ApiResponse<List<ExecutionResponse>> historyById(ScheduleId id) {
        if (!store.exists(id)) {
            throw notFound();
        }
        return ApiResponse.success(store.executionViews(id).stream().map(ExecutionResponse::from).toList());
    }

    private ApiResponse<ExecutionResponse> runById(ScheduleId id) {
        if (store.find(id).isEmpty()) {
            throw notFound();
        }
        try {
            return ApiResponse.success(scheduler.runNow(id)
                    .map(ExecutionResponse::from)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT, "SCHEDULE_BUSY", "该 Agent 的定时任务正在运行")));
        } catch (NoSuchElementException ignored) {
            throw notFound();
        } catch (TimeoutException ignored) {
            throw new AgentCallTimeoutException();
        }
    }

    private ApiResponse<ScheduleResponse> updateById(ScheduleId id, UpdateScheduleRequest request) {
        return ApiResponse.success(store.setEnabled(id, request.enabledValue())
                .map(ScheduleResponse::from).orElseThrow(ScheduleApiController::notFound));
    }

    private ScheduleId legacyId(String key) {
        var matches = store.list().stream()
                .filter(task -> task.scheduleKey().equals(key)).limit(2).toList();
        if (matches.isEmpty()) {
            throw notFound();
        }
        if (matches.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_KEY_CONFLICT", "定时任务键重复，请使用 v2 稳定 ID");
        }
        return matches.getFirst().scheduleId();
    }

    private static ScheduleId parseId(String value) {
        try {
            return new ScheduleId(value);
        } catch (IllegalArgumentException ignored) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "scheduleId 格式无效");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "定时任务不存在");
    }

    record ScheduleResponse(String scheduleId, String profileName, String scheduleKey, String displayName,
            String cron, String zone, boolean enabled, Instant nextRunAt, Instant lastRunAt,
            String lastStatus, long runCount) {

        static ScheduleResponse from(ScheduledTaskView task) {
            return new ScheduleResponse(task.scheduleId().value(), task.profileName(), task.scheduleKey(),
                    task.displayName(), task.cron(), task.zone(), task.enabled(), task.nextRunAt(),
                    task.lastRunAt(), task.lastStatus(), task.runCount());
        }
    }

    record ExecutionResponse(String scheduleId, String sessionId, Instant startedAt, boolean success,
            String errorMessage, long durationMs) {

        static ExecutionResponse from(TaskExecutionView execution) {
            return new ExecutionResponse(execution.scheduleId().value(), execution.sessionId(),
                    execution.startedAt(), execution.success(), execution.errorMessage(), execution.durationMs());
        }
    }
}
