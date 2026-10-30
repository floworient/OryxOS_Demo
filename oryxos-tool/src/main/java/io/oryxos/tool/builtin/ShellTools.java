package io.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.tool.sandbox.FileAccessAction;
import io.oryxos.tool.sandbox.FileAccessMode;
import io.oryxos.tool.sandbox.Sandbox;
import io.oryxos.tool.sandbox.ShellExecuteAction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.util.json.JsonParser;

public final class ShellTools {

    private static final int MAX_INPUT_BYTES = 256 * 1024;

    private static final int MAX_STREAM_BYTES = 256 * 1024;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private static final int MAX_TIMEOUT_SECONDS = 300;

    private static final String SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,"
            + "\"properties\":{\"executable\":{\"type\":\"string\",\"minLength\":1},"
            + "\"arguments\":{\"type\":\"array\",\"maxItems\":256,\"items\":{\"type\":\"string\"}},"
            + "\"working_directory\":{\"type\":\"string\",\"minLength\":1},"
            + "\"timeout_seconds\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":300}},"
            + "\"required\":[\"executable\",\"arguments\"]}";

    private final Sandbox sandbox;

    private final OryxTool tool;

    public ShellTools(Sandbox sandbox) {
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.tool = new OryxTool() {
            @Override
            public String getName() {
                return "shell";
            }

            @Override
            public String getDescription() {
                return "使用管理员白名单中的 executable alias 和原始 argv 执行受控命令";
            }

            @Override
            public String getInputSchema() {
                return SCHEMA;
            }

            @Override
            public ToolResult execute(String jsonInput) {
                return executeShell(jsonInput);
            }
        };
    }

    public OryxTool tool() {
        return tool;
    }

    private ToolResult executeShell(String json) {
        Process process = null;
        try {
            Input input = parse(json);
            Profile profile = ProfileContext.current();
            if (profile == null) {
                throw new SecurityException("缺少当前 Agent 上下文");
            }
            Path executable = sandbox.enforce(new ShellExecuteAction(input.executable()));
            Path requestedDirectory = Path.of(input.workingDirectory() == null ? "." : input.workingDirectory());
            Path directory = sandbox.enforce(new FileAccessAction(profile, requestedDirectory,
                    FileAccessMode.WORKING_DIRECTORY));
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("Shell 工作目录不是目录");
            }
            var command = new ArrayList<String>();
            command.add(executable.toString());
            command.addAll(input.arguments());
            process = new ProcessBuilder(command).directory(directory.toFile()).start();
            Process started = process;
            var readers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
            try {
                var stdout = readers.submit(() -> drain(started.getInputStream()));
                var stderr = readers.submit(() -> drain(started.getErrorStream()));
                if (!process.waitFor(input.timeoutSeconds(), TimeUnit.SECONDS)) {
                    if (!terminate(process)) {
                        return failed("Shell 进程树终止失败");
                    }
                    consume(stdout);
                    consume(stderr);
                    return failed("Shell 执行超时");
                }
                BoundedOutput out = stdout.get(5, TimeUnit.SECONDS);
                BoundedOutput err = stderr.get(5, TimeUnit.SECONDS);
                var result = new LinkedHashMap<String, Object>();
                result.put("exit_code", process.exitValue());
                result.put("stdout", out.text());
                result.put("stderr", err.text());
                result.put("stdout_truncated", out.truncated());
                result.put("stderr_truncated", err.truncated());
                return new ToolResult(true, JsonParser.toJson(result), null, false);
            }
            finally {
                // 先结束进程、关闭管道，再停止读取线程，避免 ExecutorService.close 无限等待阻塞读取。
                if (process.isAlive()) {
                    terminate(process);
                }
                close(process.getInputStream());
                close(process.getErrorStream());
                close(process.getOutputStream());
                readers.shutdownNow();
            }
        }
        catch (InterruptedException exception) {
            if (process != null) {
                terminate(process);
            }
            Thread.currentThread().interrupt();
            return failed("Shell 执行已中断");
        }
        catch (RuntimeException | IOException | java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException exception) {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            return failed("Shell 执行失败");
        }
    }

    private static Input parse(String json) throws IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("Shell 输入超过上限");
        }
        Object decoded = JsonParser.getObjectMapper().readerFor(Object.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
        if (!(decoded instanceof Map<?, ?> values)
                || !Set.of("executable", "arguments", "working_directory", "timeout_seconds")
                        .containsAll(values.keySet())
                || !values.keySet().containsAll(Set.of("executable", "arguments"))) {
            throw new IllegalArgumentException("Shell 输入字段非法");
        }
        if (!(values.get("executable") instanceof String executable) || executable.isBlank()
                || executable.length() > 128) {
            throw new IllegalArgumentException("Shell executable alias 非法");
        }
        if (!(values.get("arguments") instanceof List<?> rawArguments) || rawArguments.size() > 256) {
            throw new IllegalArgumentException("Shell arguments 非法");
        }
        var arguments = new ArrayList<String>();
        for (Object raw : rawArguments) {
            if (!(raw instanceof String value) || value.length() > 8192 || value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Shell argument 非法");
            }
            arguments.add(value);
        }
        String workingDirectory = null;
        if (values.containsKey("working_directory")) {
            if (!(values.get("working_directory") instanceof String value) || value.isBlank()
                    || value.length() > 4096 || value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Shell working_directory 非法");
            }
            workingDirectory = value;
        }
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        if (values.containsKey("timeout_seconds")) {
            if (!(values.get("timeout_seconds") instanceof Number number)
                    || number.doubleValue() != number.intValue()) {
                throw new IllegalArgumentException("Shell timeout_seconds 非法");
            }
            timeout = number.intValue();
            if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
                throw new IllegalArgumentException("Shell timeout_seconds 越界");
            }
        }
        return new Input(executable, List.copyOf(arguments), workingDirectory, timeout);
    }

    private static BoundedOutput drain(InputStream stream) throws IOException {
        byte[] buffer = new byte[8192];
        var kept = new java.io.ByteArrayOutputStream(MAX_STREAM_BYTES);
        boolean truncated = false;
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            int remaining = MAX_STREAM_BYTES - kept.size();
            if (remaining > 0) {
                kept.write(buffer, 0, Math.min(remaining, read));
            }
            if (read > remaining) {
                truncated = true;
            }
        }
        return new BoundedOutput(kept.toString(StandardCharsets.UTF_8), truncated);
    }

    private static boolean terminate(Process process) {
        var handles = new ArrayList<>(process.descendants().toList());
        java.util.Collections.reverse(handles);
        handles.add(process.toHandle());
        handles.forEach(handle -> {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        try {
            while (handles.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
                handles.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                Thread.sleep(20);
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
        finally {
            close(process.getOutputStream());
            close(process.getInputStream());
            close(process.getErrorStream());
        }
        return handles.stream().noneMatch(ProcessHandle::isAlive);
    }

    private static void close(java.io.Closeable stream) {
        try {
            stream.close();
        }
        catch (IOException ignored) {
            // 进程终止后关闭管道只用于释放本地句柄。
        }
    }

    private static void consume(java.util.concurrent.Future<BoundedOutput> output) {
        try {
            output.get(5, TimeUnit.SECONDS);
        }
        catch (RuntimeException | InterruptedException | java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ToolResult failed(String message) {
        return new ToolResult(false, "", message, false);
    }

    private record Input(String executable, List<String> arguments, String workingDirectory,
                         int timeoutSeconds) {
    }

    private record BoundedOutput(String text, boolean truncated) {
    }
}
