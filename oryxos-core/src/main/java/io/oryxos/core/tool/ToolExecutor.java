package io.oryxos.core.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.audit.ToolInvocationRecord;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.interaction.UserInteraction;
import io.oryxos.core.interaction.UserInteractionContext;
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

    private final ToolRegistry tools;

    private final BiConsumer<Profile, ToolCallIntent> policy;

    private final ToolInvocationRecorder recorder;

    private final ExecutorService worker;

    private final Duration timeout;

    private final LongConsumer sleeper;

    public ToolExecutor(ToolRegistry tools, BiConsumer<Profile, ToolCallIntent> policy,
            ToolInvocationRecorder recorder) {
        this(tools, policy, recorder, Executors.newVirtualThreadPerTaskExecutor(),
                Duration.ofSeconds(60), ToolExecutor::sleep);
    }

    public ToolExecutor(ToolRegistry tools, BiConsumer<Profile, ToolCallIntent> policy,
            ToolInvocationRecorder recorder, ExecutorService worker, Duration timeout, LongConsumer sleeper) {
        this.tools = java.util.Objects.requireNonNull(tools);
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
        long started = System.nanoTime();
        for (int attempt = 0; ; attempt++) {
            ToolResult result;
            try {
                OryxTool tool = named && profile != null ? visibleTool(profile, toolName) : null;
                if (!named) {
                    result = failed("工具调用名称非法");
                }
                else if (profile == null || tool == null) {
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
            if (!result.success() && result.retryable() && attempt < 3) {
                try {
                    sleeper.accept(100L << attempt);
                    continue;
                }
                catch (RuntimeException exception) {
                    result = failed("工具重试等待已中断或失败");
                }
            }
            try {
                recorder.record(new ToolInvocationRecord(sessionId, named ? toolName : "<invalid>",
                        auditInput(toolName, call.argumentsJson()), JsonParser.toJson(result),
                        result.success(), result.errorMessage(),
                        Math.max(0, (System.nanoTime() - started) / 1_000_000), Instant.now()));
            }
            catch (RuntimeException exception) {
                throw new IllegalStateException("工具审计持久化失败", exception);
            }
            return result;
        }
    }

    private OryxTool visibleTool(Profile profile, String toolName) {
        return tools.visibleTo(profile).stream()
                .filter(registration -> registration.name().equals(toolName))
                .map(ToolRegistration::tool)
                .findFirst()
                .orElse(null);
    }

    private ToolResult checkedExecute(Profile profile, ToolCallIntent call, OryxTool tool) {
        try {
            policy.accept(profile, call);
        }
        catch (RuntimeException exception) {
            return failed("工具安全检查拒绝");
        }
        UserInteraction interaction = UserInteractionContext.current();
        var future = worker.submit(() -> {
            ProfileContext.set(profile);
            if (interaction != null) {
                UserInteractionContext.set(interaction);
            }
            try {
                return tool.execute(call.argumentsJson());
            }
            finally {
                UserInteractionContext.clear();
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

    private String auditInput(String name, String input) {
        try {
            if ("save_memory".equals(name)) {
                return auditMemorySave(input);
            }
            if (name == null || tools.find(name).map(item -> item.source() != ToolSource.BUILTIN).orElse(true)) {
                byte[] bytes = (input == null ? "" : input).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
                return JsonParser.toJson(Map.of("bytes", bytes.length,
                        "sha256", java.util.HexFormat.of().formatHex(hash)));
            }
            var value = JsonParser.getObjectMapper().readTree(input);
            redact(value);
            return value.toString();
        }
        catch (java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException exception) {
            return "{\"redacted\":true}";
        }
    }

    private static String auditMemorySave(String input)
            throws java.io.IOException, java.security.NoSuchAlgorithmException {
        var value = JsonParser.getObjectMapper().readTree(input);
        String content = value.path("content").isTextual() ? value.path("content").asText() : "";
        String requestedScope = value.path("scope").isTextual()
                ? value.path("scope").asText().trim().toLowerCase(java.util.Locale.ROOT) : "archival";
        String scope = requestedScope.equals("core") || requestedScope.equals("archival")
                ? requestedScope : "<invalid>";
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        return JsonParser.toJson(Map.of("scope", scope, "bytes", bytes.length,
                "sha256", java.util.HexFormat.of().formatHex(hash)));
    }

    private static void redact(com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            var fields = new java.util.ArrayList<String>();
            object.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                String key = field.toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
                if (key.contains("token") || key.contains("secret") || key.contains("password")
                        || key.contains("authorization") || key.contains("cookie") || key.contains("apikey")
                        || key.equals("headers") || key.equals("url") || key.equals("webhook")) {
                    object.put(field, "[REDACTED]");
                }
                else {
                    redact(object.get(field));
                }
            }
        }
        else if (node != null && node.isArray()) {
            node.forEach(ToolExecutor::redact);
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
