package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.core.tool.ToolRegistry;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(OutputCaptureExtension.class)
class SandboxAuditIT {

    @TempDir
    Path temporary;

    @Test
    void deniedOperationsHaveNoBusinessIoAndOneSafePersistedAudit(CapturedOutput output) throws Exception {
        Path workspace = Files.createDirectories(temporary.resolve("workspace"));
        Path agent = Files.createDirectories(workspace.resolve("agents/runner"));
        Path source = Files.writeString(agent.resolve("AGENT.md"), "---\nname: runner\n---\n测试");
        Path outside = Files.writeString(temporary.resolve("outside.txt"), "unchanged");
        Path marker = agent.resolve("started.txt");
        String canary = "canary-audit-private-not-real";
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String blocked = "http://localhost:" + port + "/hook?token=" + canary;
            String allowed = "http://127.0.0.1:" + port + "/hook";
            var application = new SpringApplication(NotifyToolAuditIT.TestApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            try (var context = application.run(
                    "--oryxos.workspace=" + workspace,
                    "--spring.datasource.url=jdbc:sqlite:" + temporary.resolve("sandbox-audit.db"),
                    "--spring.jpa.hibernate.ddl-auto=validate",
                    "--spring.flyway.locations=classpath:db/migration/sqlite",
                    "--oryxos.http.allowed-domains[0]=127.0.0.1",
                    "--oryxos.tool.allowed-commands.safe-java="
                            + ProcessHandle.current().info().command().orElseThrow())) {
                var channels = context.getBean(NotifyChannelService.class);
                channels.create(new NotifyChannelService.CreateCommand("blocked", "webhook", blocked,
                        null, Map.of()));
                channels.create(new NotifyChannelService.CreateCommand("allowed", "webhook", allowed,
                        null, Map.of()));
                var profile = new Profile("runner", "测试", new Profile.Identity("助手", "测试"),
                        new Profile.ProviderRef("stub", "stub", null),
                        List.of("read_file", "write_file", "shell", "http_get", "http_post", "notify"),
                        List.of(), List.of(), List.of(), List.of(), new Profile.Settings(3, 10), source);
                ProfileContext.set(profile);
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                try (var executor = new ToolExecutor(context.getBean(ToolRegistry.class), (p, call) -> { },
                        context.getBean(ToolInvocationRecorder.class))) {
                    var calls = List.of(
                            new Denied("write_file", Map.of("path", outside.toString(), "content", "changed"),
                                    "文件访问被 Sandbox 拒绝"),
                            new Denied("shell", Map.of("executable", "unconfigured",
                                    "arguments", helperArguments(marker, canary)), "Shell 命令被 Sandbox 拒绝"),
                            new Denied("http_get", Map.of("url", blocked), "HTTP 请求被 Sandbox 拒绝"),
                            new Denied("http_post", Map.of("url", blocked, "body", "hello"),
                                    "HTTP 请求被 Sandbox 拒绝"),
                            new Denied("notify", Map.of("channel", "blocked", "content", "hello"),
                                    "HTTP 请求被 Sandbox 拒绝"));
                    for (Denied call : calls) {
                        String session = "denied-" + call.tool();
                        var result = executor.execute(session,
                                new ToolCallIntent(session, call.tool(), JsonParser.toJson(call.input())));
                        assertThat(result.success()).isFalse();
                        assertThat(result.retryable()).isFalse();
                        assertThat(result.errorMessage()).isEqualTo(call.reason());
                        var rows = jdbc.queryForList("select * from tool_invocations where session_id=?", session);
                        assertThat(rows).singleElement().satisfies(row -> {
                            assertThat(((Number) row.get("success")).intValue()).isZero();
                            assertThat(row.get("error_message")).isEqualTo(call.reason());
                            assertThat(row.toString()).doesNotContain(canary);
                        });
                    }
                    assertThat(Files.readString(outside)).isEqualTo("unchanged");
                    assertThat(marker).doesNotExist();
                    assertThat(requests).hasValue(0);

                    assertSuccess(executor, jdbc, "read_file", Map.of("path", "AGENT.md"));
                    assertSuccess(executor, jdbc, "write_file", Map.of("path", "written.txt", "content", "once"));
                    assertSuccess(executor, jdbc, "shell", Map.of("executable", "safe-java",
                            "arguments", helperArguments(marker, "literal")));
                    assertThat(Files.readString(marker)).isEqualTo("started\n");
                    assertSuccess(executor, jdbc, "http_get", Map.of("url", allowed));
                    assertSuccess(executor, jdbc, "http_post", Map.of("url", allowed, "body", "hello"));
                    assertSuccess(executor, jdbc, "notify", Map.of("channel", "allowed", "content", "hello"));
                    assertThat(requests).hasValue(3);
                }
                finally {
                    ProfileContext.clear();
                }
            }
            assertThat(output.getAll()).doesNotContain(canary);
        }
        finally {
            server.stop(0);
        }
    }

    private static void assertSuccess(ToolExecutor executor, JdbcTemplate jdbc, String name, Map<String, ?> input) {
        String session = "allowed-" + name;
        assertThat(executor.execute(session, new ToolCallIntent(session, name, JsonParser.toJson(input))).success())
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from tool_invocations where session_id=? and success=1",
                Integer.class, session)).isEqualTo(1);
    }

    private static List<String> helperArguments(Path marker, String value) throws java.net.URISyntaxException {
        // helper 仅依赖 JDK，避免完整 Boot 测试 classpath 超出生产 argv 单参数上限。
        String classpath = Path.of(Helper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return List.of("-cp", classpath, Helper.class.getName(), marker.toString(), value);
    }

    private record Denied(String tool, Map<String, ?> input, String reason) {
    }

    public static final class Helper {
        private Helper() {
        }

        public static void main(String[] arguments) throws Exception {
            Files.writeString(Path.of(arguments[0]), "started\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
    }
}
