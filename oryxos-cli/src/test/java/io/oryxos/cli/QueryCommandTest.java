package io.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueryCommandTest {
    @TempDir
    Path workspace;

    @Test
    void providerToolAndSessionListsPrintHeadersUnicodeAndStableOrdering() {
        String longName = "中文-" + "很长".repeat(40);
        CliRuntime runtime = new EmptyRuntime() {
            @Override
            public List<ProviderInfo> providers(Path root) {
                return List.of(
                        new ProviderInfo(longName, "DASHSCOPE", "模型", "https://example.test"),
                        new ProviderInfo("alpha", "OPENAI_COMPATIBLE", "gpt", null));
            }

            @Override
            public List<ToolInfo> tools(Path root) {
                return List.of(new ToolInfo("天气", "查询天气"), new ToolInfo("alpha", longName));
            }

            @Override
            public List<SessionInfo> sessions(Path root) {
                return List.of(
                        new SessionInfo("old", "天气", "active", Instant.parse("2026-10-27T00:00:00Z")),
                        new SessionInfo("b", "摘要", "archived", Instant.parse("2026-10-28T00:00:00Z")),
                        new SessionInfo("a", "摘要", "active", Instant.parse("2026-10-28T00:00:00Z")));
            }
        };

        Result providers = execute(runtime, "provider", "list");
        assertThat(providers.exit()).isZero();
        assertThat(providers.error()).isEmpty();
        assertThat(providers.output()).startsWith("NAME\tTYPE\tMODEL\tBASE_URL")
                .contains(longName, "alpha", "-");
        assertThat(providers.output().indexOf("alpha")).isLessThan(providers.output().indexOf(longName));

        Result tools = execute(runtime, "tool", "list");
        assertThat(tools.exit()).isZero();
        assertThat(tools.error()).isEmpty();
        assertThat(tools.output()).startsWith("NAME\tDESCRIPTION").contains("天气", longName);
        assertThat(tools.output().indexOf("alpha")).isLessThan(tools.output().indexOf("天气"));

        Result sessions = execute(runtime, "session", "list");
        assertThat(sessions.exit()).isZero();
        assertThat(sessions.error()).isEmpty();
        assertThat(sessions.output()).startsWith("SESSION_ID\tPROFILE\tSTATUS\tLAST_ACTIVE_AT");
        assertThat(sessions.output().indexOf("a\t")).isLessThan(sessions.output().indexOf("b\t"));
        assertThat(sessions.output().indexOf("b\t")).isLessThan(sessions.output().indexOf("old\t"));
    }

    @Test
    void emptyListsAreSuccessfulAndExplicit() {
        var runtime = new EmptyRuntime();
        assertThat(execute(runtime, "provider", "list")).isEqualTo(new Result(0, "未配置 Provider\n", ""));
        assertThat(execute(runtime, "tool", "list")).isEqualTo(new Result(0, "未注册 Tool\n", ""));
        assertThat(execute(runtime, "session", "list")).isEqualTo(new Result(0, "未找到 Session\n", ""));
    }

    private Result execute(CliRuntime runtime, String... args) {
        var output = new StringWriter();
        var error = new StringWriter();
        int exit = new OryxOsCli(runtime, workspace, new StringReader(""),
                new PrintWriter(output, true), new PrintWriter(error, true), () -> "alice").execute(args);
        return new Result(exit, normalized(output), normalized(error));
    }

    private static String normalized(StringWriter value) {
        return value.toString().replace("\r\n", "\n");
    }

    private record Result(int exit, String output, String error) { }

    private static class EmptyRuntime implements CliRuntime {
        public void chat(Path root, String profile, String user, Reader input, PrintWriter output) { }

        public void serve(Path root, int port) { }

        public void gateway(Path root) { }

        public List<ProviderInfo> providers(Path root) { return List.of(); }

        public List<ToolInfo> tools(Path root) { return List.of(); }

        public List<SessionInfo> sessions(Path root) { return List.of(); }
    }
}
