package io.oryxos.core.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.audit.ToolInvocationRecord;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.provider.ToolCallIntent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import org.springframework.ai.util.json.JsonParser;

public final class ToolExecutor implements AutoCloseable {

    private final Map<String, OryxTool> tools;

    private final BiConsumer<Profile, ToolCallIntent> policy;

    private final ToolInvocationRecorder recorder;

    private final ExecutorService worker;

    private final Duration timeout;

    private final LongConsumer sleeper;

    public ToolExecutor(Map<String, OryxTool> tools, BiConsumer<Profile, ToolCallIntent> policy,
            ToolInvocationRecorder recorder) {
        this(tools, policy, recorder, Executors.newVirtualThreadPerTaskExecutor(),
                Duration.ofSeconds(60), ToolExecutor::sleep);
    }

    public ToolExecutor(Map<String, OryxTool> tools, BiConsumer<Profile, ToolCallIntent> policy,
            ToolInvocationRecorder recorder, ExecutorService worker, Duration timeout, LongConsumer sleeper) {
        this.tools = Map.copyOf(tools);
        this.policy = java.util.Objects.requireNonNull(policy);
        this.recorder = java.util.Objects.requireNonNull(recorder);
        this.worker = worker;
        this.timeout = timeout;
        this.sleeper = sleeper;
    }

    public ToolResult execute(String sessionId, ToolCallIntent call) {
        Profile profile = ProfileContext.current();
        String toolName = call.name();
        boolean named = toolName != null && !toolName.isBlank();
        for (int attempt = 0; ; attempt++) {
            long started = System.nanoTime();
            ToolResult result;
            try {
                OryxTool tool = named ? tools.get(toolName) : null;
                if (!named) {
                    result = failed("工具调用名称非法");
                }
                else if (profile == null || tool == null || !profile.tools().contains(toolName)) {
                    result = failed("工具未注册或当前 Agent 不可见");
                }
                else if (call.id() == null || call.id().isBlank() || !validInput(call.argumentsJson())) {
                    result = failed("工具调用标识或参数非法");
                }
                else {
                    result = checkedExecute(profile, call, tool);
                }
            }
            catch (RuntimeException exception) {
                result = failed("工具执行失败");
            }
            if (result == null) {
                result = failed("工具未返回结果");
            }
            if (!result.success() && (result.errorMessage() == null || result.errorMessage().isBlank())) {
                result = new ToolResult(false, result.content(), "工具返回失败", result.retryable());
            }
            try {
                recorder.record(new ToolInvocationRecord(sessionId, named ? toolName : "<invalid>",
                        call.argumentsJson() == null ? "null" : call.argumentsJson(), JsonParser.toJson(result),
                        result.success(), result.errorMessage(),
                        Math.max(0, (System.nanoTime() - started) / 1_000_000), Instant.now()));
            }
            catch (RuntimeException exception) {
                throw new IllegalStateException("工具审计持久化失败", exception);
            }
            if (result.success() || !result.retryable() || attempt >= 3) {
                return result;
            }
            sleeper.accept(100L << attempt);
        }
    }

    private ToolResult checkedExecute(Profile profile, ToolCallIntent call, OryxTool tool) {
        try {
            policy.accept(profile, call);
        }
        catch (RuntimeException exception) {
            return failed("工具安全检查拒绝");
        }
        var future = worker.submit(() -> {
            ProfileContext.set(profile);
            try {
                return tool.execute(call.argumentsJson());
            }
            finally {
                ProfileContext.clear();
            }
        });
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException exception) {
            future.cancel(true);
            return failed("工具执行超时");
        }
        catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return failed("工具执行已中断");
        }
        catch (ExecutionException exception) {
            return failed("工具执行异常");
        }
    }

    private static boolean validInput(String input) {
        try {
            return JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(input) instanceof Map<?, ?>;
        }
        catch (java.io.IOException | RuntimeException exception) {
            return false;
        }
    }

    private static ToolResult failed(String reason) {
        return new ToolResult(false, "", reason, false);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("工具重试等待已中断", exception);
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
