package io.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliFailureHandlerTest {

    @TempDir
    Path root;

    @Test
    void normalOutputUsesStdoutAndNotStderr() {
        var streams = execute(new EmptyRuntime(), "--version");

        assertThat(streams.exitCode()).isZero();
        assertThat(streams.stdout()).contains("OryxOS");
        assertThat(streams.stderr()).isEmpty();
    }

    @Test
    void executionFailuresUseStderrExitOneAndRedactCredentialsWithoutStackTrace() {
        CliRuntime failing = new EmptyRuntime() {
            @Override
            public List<ProviderInfo> providers(Path workspace) {
                throw new IllegalStateException(
                        "Authorization: Bearer secret-token api-key=alpha token=bravo");
            }
        };

        var streams = execute(failing, "provider", "list");

        assertThat(streams.exitCode()).isEqualTo(1);
        assertThat(streams.stdout()).isEmpty();
        assertThat(streams.stderr())
                .contains("错误")
                .doesNotContain("secret-token", "alpha", "bravo", "IllegalStateException", "\tat ");
    }

    private Result execute(CliRuntime runtime, String... args) {
        var stdout = new StringWriter();
        var stderr = new StringWriter();
        var cli = new OryxOsCli(runtime, root, new StringReader(""),
                new PrintWriter(stdout, true), new PrintWriter(stderr, true), () -> "alice");
        int exitCode = cli.execute(args);
        return new Result(exitCode, stdout.toString(), stderr.toString());
    }

    private record Result(int exitCode, String stdout, String stderr) { }

    private static class EmptyRuntime implements CliRuntime {
        @Override
        public void chat(Path workspace, String profileName, String userId, Reader input, PrintWriter output) { }

        @Override
        public void serve(Path workspace, int port) { }

        @Override
        public void gateway(Path workspace) { }

        @Override
        public List<ProviderInfo> providers(Path workspace) {
            return List.of();
        }

        @Override
        public List<ToolInfo> tools(Path workspace) {
            return List.of();
        }

        @Override
        public List<SessionInfo> sessions(Path workspace) {
            return List.of();
        }
    }
}
