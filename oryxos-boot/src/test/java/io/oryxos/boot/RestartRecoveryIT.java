package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.memory.MarkdownMemoryStore;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
@EnabledIfSystemProperty(named = "oryxos.scheduler.it", matches = "true")
class RestartRecoveryIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SCHEDULES = "/api/v2/schedules";

    private static final String FACT = "我关注 AI 和芯片";

    @TempDir
    Path workspace;

    @Test
    void forciblyRestartsPackagedServeWithExistingState() throws Exception {
        SchedulerAcceptanceConfig.prepare(workspace);
        prepareWorkspace();
        String id = ScheduleId.from("recovery", "daily").value();
        String sessionId;
        JsonNode messages;
        long audit;
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            int port = availablePort();
            Process first = start(port);
            try {
                awaitReady(client, first, port);
                var firstRun = api(client, port, "POST", SCHEDULES + "/" + id + "/run", null);
                assertThat(firstRun.path("success").asBoolean()).isTrue();
                sessionId = firstRun.path("sessionId").asText();
                assertThat(sessionId).isNotBlank();
                messages = api(client, port, "GET", "/api/v1/sessions/" + sessionId, null).path("messages");
                assertThat(messages).hasSizeGreaterThanOrEqualTo(2);
                api(client, port, "PUT", SCHEDULES + "/" + id, "{\"enabled\":false}");
                audit = countAudit(sessionId);
                assertThat(audit).isPositive();
            } finally {
                stop(first);
            }
            int restartedPort = availablePort();
            Process second = start(restartedPort);
            try {
                awaitReady(client, second, restartedPort);
                assertThat(api(client, restartedPort, "GET", "/api/v1/sessions/" + sessionId, null).path("messages"))
                        .isEqualTo(messages);
                assertThat(api(client, restartedPort, "GET", "/api/v1/memory?agentName=recovery", null)
                        .path("core").get(0).path("content").asText()).isEqualTo(FACT);
                JsonNode task = api(client, restartedPort, "GET", SCHEDULES, null).get(0);
                assertThat(task.path("scheduleId").asText()).isEqualTo(id);
                assertThat(task.path("enabled").asBoolean(true)).isFalse();
                assertThat(task.path("runCount").asInt()).isEqualTo(1);
                assertThat(task.path("lastStatus").asText()).isEqualTo("success");
                assertThat(Instant.parse(task.path("nextRunAt").asText())).isAfter(Instant.now());
                assertThat(api(client, restartedPort, "GET", SCHEDULES + "/" + id + "/executions", null))
                        .hasSize(1);
                assertThat(countAudit(sessionId)).isEqualTo(audit);
                var secondRun = api(client, restartedPort, "POST", SCHEDULES + "/" + id + "/run", null);
                assertThat(secondRun.path("success").asBoolean()).isTrue();
                assertThat(secondRun.path("sessionId").asText()).isEqualTo(sessionId);
                var continued = api(client, restartedPort, "GET", "/api/v1/sessions/" + sessionId, null)
                        .path("messages");
                assertThat(continued).hasSizeGreaterThan(messages.size());
                assertThat(continued.get(continued.size() - 1).path("text").asText()).contains("AI", "芯片");
                assertThat(countAudit(sessionId)).isGreaterThan(audit);
            } finally {
                stop(second);
            }
        }
    }

    private void prepareWorkspace() throws IOException {
        Path agent = Files.createDirectories(workspace.resolve("agents/recovery"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: recovery
                description: 进程重启验收
                identity: {agent_name: 恢复助手, prompt: 回答测试问题}
                provider: {name: qwen, model: qwen-plus}
                schedules:
                  - {key: daily, cron: "0 0 8 1 1 *", zone: Asia/Shanghai, message: "简述我关注的领域"}
                ---
                根据核心记忆，用一句话复述用户关注的领域。
                """);
        Files.writeString(agent.resolve("MEMORY.md"), "# MEMORY\n\n## 核心记忆\n\n## 归档记忆\n");
        new MarkdownMemoryStore(workspace, Clock.systemUTC(), ZoneId.of("Asia/Shanghai"), 4000, 20, 4000)
                .append(MemoryOwner.agent("recovery"), FACT, MemoryScope.CORE);
    }

    private Process start(int port) throws IOException {
        Path jar;
        try (var files = Files.list(Path.of("target"))) {
            jar = files.filter(path -> path.getFileName().toString().endsWith("-exec.jar")).findFirst().orElseThrow();
        }
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Doryxos.root=" + workspace, "-jar", jar.toAbsolutePath().toString(),
                "serve", "--port", Integer.toString(port));
        command.environment().remove("JAVA_TOOL_OPTIONS");
        command.environment().remove("_JAVA_OPTIONS");
        command.environment().remove("JDK_JAVA_OPTIONS");
        return command.redirectErrorStream(true).redirectOutput(workspace.resolve("serve-" + port + ".log").toFile())
                .start();
    }

    private static void stop(Process process) throws InterruptedException {
        process.destroyForcibly();
        assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
    }

    private static int availablePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitReady(HttpClient client, Process process, int port) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (process.isAlive() && Instant.now().isBefore(deadline)) {
            try {
                var response = client.send(HttpRequest.newBuilder(uri(port, "/api/v1/health"))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // 服务进程尚未监听。
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("测试服务进程未在 45 秒内就绪");
    }

    private static JsonNode api(HttpClient client, int port, String method, String path, String body)
            throws Exception {
        var request = HttpRequest.newBuilder(uri(port, path)).timeout(Duration.ofSeconds(65))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(200);
        return JSON.readTree(response.body()).path("data");
    }

    private static URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private long countAudit(String sessionId) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + workspace.resolve("oryxos.db"));
                var query = connection.prepareStatement("""
                        select count(*) from llm_calls where session_id=? and status='SUCCESS'
                        """)) {
            query.setString(1, sessionId);
            try (var rows = query.executeQuery()) {
                return rows.getLong(1);
            }
        }
    }
}
