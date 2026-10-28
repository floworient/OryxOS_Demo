package io.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OryxOsCliCommandTreeTest {

    @TempDir
    Path root;

    private final AtomicInteger runtimeCalls = new AtomicInteger();

    private final StringWriter stdout = new StringWriter();

    private final StringWriter stderr = new StringWriter();

    private OryxOsCli cli;

    @BeforeEach
    void setUp() {
        cli = new OryxOsCli(new FakeRuntime(), root, new StringReader(""),
                new PrintWriter(stdout, true), new PrintWriter(stderr, true), () -> "alice");
    }

    @Test
    void rootGroupsAndTwelveBusinessPathsExposeHelpWithoutStartingRuntime() {
        assertSuccessWithUsage("--help");
        assertSuccessWithUsage("profile", "--help");
        assertSuccessWithUsage("provider", "--help");
        assertSuccessWithUsage("tool", "--help");
        assertSuccessWithUsage("session", "--help");

        List<List<String>> paths = List.of(
                List.of("init"), List.of("status"), List.of("chat"), List.of("serve"),
                List.of("gateway"), List.of("profile", "list"), List.of("profile", "create"),
                List.of("profile", "show"), List.of("profile", "delete"),
                List.of("provider", "list"), List.of("tool", "list"),
                List.of("session", "list"));
        paths.forEach(path -> {
            var args = new java.util.ArrayList<>(path);
            args.add("--help");
            assertSuccessWithUsage(args.toArray(String[]::new));
        });

        assertThat(runtimeCalls).hasValue(0);
    }

    @Test
    void bareGroupsPrintTheirOwnHelpAndVersionDoesNotStartRuntime() {
        for (String group : List.of("profile", "provider", "tool", "session")) {
            stdout.getBuffer().setLength(0);
            assertThat(cli.execute(group)).isZero();
            assertThat(stdout.toString()).contains("Usage: oryxos " + group);
        }

        stdout.getBuffer().setLength(0);
        assertThat(cli.execute("--version")).isZero();
        assertThat(stdout.toString()).contains("OryxOS 0.1.0-SNAPSHOT");
        assertThat(runtimeCalls).hasValue(0);
    }

    @Test
    void unknownCommandsAndMissingRequiredArgumentsUseUsageExitCode() {
        assertThat(cli.execute("missing")).isEqualTo(2);
        assertThat(cli.execute("profile", "create")).isEqualTo(2);
        assertThat(cli.execute("profile", "show")).isEqualTo(2);
        assertThat(cli.execute("profile", "delete")).isEqualTo(2);
        assertThat(runtimeCalls).hasValue(0);
    }

    private void assertSuccessWithUsage(String... args) {
        stdout.getBuffer().setLength(0);
        stderr.getBuffer().setLength(0);
        assertThat(cli.execute(args)).isZero();
        assertThat(stdout.toString()).contains("Usage:");
        assertThat(stderr.toString()).isEmpty();
    }

    private final class FakeRuntime implements CliRuntime {
        @Override
        public void chat(Path workspace, String profileName, String userId, Reader input, PrintWriter output) {
            runtimeCalls.incrementAndGet();
        }

        @Override
        public void serve(Path workspace, int port) {
            runtimeCalls.incrementAndGet();
        }

        @Override
        public void gateway(Path workspace) {
            runtimeCalls.incrementAndGet();
        }

        @Override
        public List<ProviderInfo> providers(Path workspace) {
            runtimeCalls.incrementAndGet();
            return List.of();
        }

        @Override
        public List<ToolInfo> tools(Path workspace) {
            runtimeCalls.incrementAndGet();
            return List.of();
        }

        @Override
        public List<SessionInfo> sessions(Path workspace) {
            runtimeCalls.incrementAndGet();
            return List.of();
        }
    }
}
