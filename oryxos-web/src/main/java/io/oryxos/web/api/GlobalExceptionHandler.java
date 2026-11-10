package io.oryxos.web.api;

import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.notify.NotifyChannelConflictException;
import io.oryxos.core.notify.NotifyChannelNotFoundException;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.core.context.ContextBudgetExceededException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将 Web 层异常统一转换为稳定且不泄露内部信息的 API 响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(NotifyChannelNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotifyChannelNotFound(NotifyChannelNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOTIFY_CHANNEL_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(NotifyChannelConflictException.class)
    ResponseEntity<ApiResponse<Void>> handleNotifyChannelConflict(NotifyChannelConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("NOTIFY_CHANNEL_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(ContextBudgetExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleContextBudget(ContextBudgetExceededException exception) {
        return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.error("CONTEXT_BUDGET_EXCEEDED", exception.getMessage()));
    }

    @ExceptionHandler(MemoryException.class)
    ResponseEntity<ApiResponse<Void>> handleMemoryException(MemoryException exception) {
        log.warn("记忆服务读取失败 code={}", exception.code());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("MEMORY_UNAVAILABLE", "记忆服务暂不可用"));
    }

    @ExceptionHandler(ProviderException.class)
    ResponseEntity<ApiResponse<Void>> handleProviderException(ProviderException exception) {
        log.warn("Provider 调用失败 code={} provider={} rootCause={} message={}",
                exception.errorCode(), exception.providerName(),
                exception.getCause() != null ? exception.getCause().getClass().getName() : "n/a",
                exception.getCause() != null ? String.valueOf(exception.getCause().getMessage()) : "n/a");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("PROVIDER_UNAVAILABLE", "模型服务暂不可用"));
    }

    @ExceptionHandler(AgentCallTimeoutException.class)
    ResponseEntity<ApiResponse<Void>> handleAgentCallTimeout(AgentCallTimeoutException exception) {
        log.warn("Agent 同步调用超过截止时间");
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.error("AGENT_TIMEOUT", "Agent 调用超时"));
    }

    @ExceptionHandler(SessionStorageException.class)
    ResponseEntity<ApiResponse<Void>> handleSessionStorage(SessionStorageException exception) {
        log.error("会话存储操作失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("STORAGE_ERROR", "会话存储暂不可用"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("未处理的 HTTP 请求异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
    }
}
