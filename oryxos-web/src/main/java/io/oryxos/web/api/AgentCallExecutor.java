package io.oryxos.web.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 在受管执行器上以统一截止时间执行同步 Agent 调用。 */
public final class AgentCallExecutor {

    private final ExecutorService executor;

    private final Duration timeout;

    public AgentCallExecutor(ExecutorService executor, Duration timeout) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout 必须大于零");
        }
    }

    public <T> T execute(Callable<T> call) {
        Future<T> future = executor.submit(Objects.requireNonNull(call, "call"));
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        catch (TimeoutException exception) {
            future.cancel(true);
            throw new AgentCallTimeoutException();
        }
        catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent 调用被中断", exception);
        }
        catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Agent 调用失败", cause);
        }
    }
}
