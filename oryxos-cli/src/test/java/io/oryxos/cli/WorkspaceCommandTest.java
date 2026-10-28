package io.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.profile.AgentLoader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceCommandTest {
    @TempDir
    Path root;

    private final StringWriter stdout = new StringWriter();

    private final StringWriter stderr = new StringWriter();

    private OryxOsCli cli;

    @BeforeEach
    void setUp() {
        cli = new OryxOsCli(new EmptyRuntime(), root, new StringReader(""),
                new PrintWriter(stdout, true), new PrintWriter(stderr, true), () -> "alice");
    }

    @Test
    void initStatusAndProfileCommandsExposeRealWorkspaceState() {
        assertSuccess(new String[] {"init"}, "created=");
        assertSuccess(new String[] {"status"}, "initialized=true", "agentCount=0");
        assertSuccess(new String[] {"profile", "create", "weather"}, "已创建 Agent: weather");
        assertThat(new AgentLoader().loadAll(root)).extracting("name").containsExactly("weather");
        assertSuccess(new String[] {"profile", "list"}, "weather");
        assertSuccess(new String[] {"profile", "show", "weather"}, "name: weather");
        assertSuccess(new String[] {"profile", "delete", "weather"}, "已归档 Agent: archive/weather-");
        assertSuccess(new String[] {"profile", "list"}, "未找到 Agent");
    }

    @Test
    void duplicateAndUnknownProfilesUseExecutionFailureChannel() {
        assertSuccess(new String[] {"init"}, "created=");
        assertSuccess(new String[] {"profile", "create", "demo"}, "已创建 Agent: demo");
        assertFailure("profile", "create", "demo");
        assertFailure("profile", "show", "missing");
        assertFailure("profile", "delete", "missing");
    }

    private void assertSuccess(String[] args, String... expected) {
        stdout.getBuffer().setLength(0);
        stderr.getBuffer().setLength(0);
        assertThat(cli.execute(args)).isZero();
        assertThat(stdout.toString()).contains(expected);
        assertThat(stderr.toString()).isEmpty();
    }

    private void assertFailure(String... args) {
        stdout.getBuffer().setLength(0);
        stderr.getBuffer().setLength(0);
        assertThat(cli.execute(args)).isEqualTo(1);
        assertThat(stdout.toString()).isEmpty();
        assertThat(stderr.toString()).contains("错误");
    }

    private static class EmptyRuntime implements CliRuntime {
        public void chat(Path workspace, String profileName, String userId, Reader input, PrintWriter output) { }

        public void serve(Path workspace, int port) { }

        public void gateway(Path workspace) { }

        public List<ProviderInfo> providers(Path workspace) { return List.of(); }

        public List<ToolInfo> tools(Path workspace) { return List.of(); }

        public List<SessionInfo> sessions(Path workspace) { return List.of(); }
    }
}
