package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.tool.sandbox.WhitelistSandbox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellToolsTest {

    @TempDir
    Path workspace;

    private ShellTools shell;

    private String classpath;

    @BeforeEach
    void setUp() throws Exception {
        Path source = Files.createDirectories(workspace.resolve("agents/shell-agent")).resolve("AGENT.md");
        Files.writeString(source, "---\nname: shell-agent\n---\n正文");
        var profile = new Profile("shell-agent", "说明", new Profile.Identity("助手", "身份"),
                new Profile.ProviderRef("p", "m", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(3, 10), source);
        ProfileContext.set(profile);
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow());
        var sandbox = new WhitelistSandbox(Set.of(), Map.of("java", java),
                new SkillBindingResolver(workspace));
        shell = new ShellTools(sandbox);
        classpath = System.getProperty("java.class.path");
    }

    @AfterEach
    void clear() {
        ProfileContext.clear();
    }

    @Test
    void passesMetacharactersAsLiteralArgvAndUsesApprovedWorkingDirectory() {
        ToolResult result = execute(List.of("echo", "a&b", "$(touch nope)", "x;y"), null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("a&b|$(touch nope)|x;y", "\"exit_code\":0");
        assertThat(Files.exists(workspace.resolve("agents/shell-agent/nope"))).isFalse();
    }

    @Test
    void drainsAndBoundsStdoutAndStderrWithoutDeadlock() {
        ToolResult result = execute(List.of("flood"), null, 10);

        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("\"stdout_truncated\":true", "\"stderr_truncated\":true");
        assertThat(result.content().length()).isLessThan(600_000);
    }

    @Test
    void timeoutTerminatesProcessAndUnknownAliasNeverStarts() throws Exception {
        Path pidFile = workspace.resolve("agents/shell-agent/pids.txt");
        Profile profile = ProfileContext.current();
        var execution = CompletableFuture.supplyAsync(() -> {
            ProfileContext.set(profile);
            try {
                return execute(List.of("tree", pidFile.toString()), null, 10);
            }
            finally {
                ProfileContext.clear();
            }
        });
        awaitNonEmpty(pidFile, execution);
        ToolResult timeout = execution.get(12, TimeUnit.SECONDS);
        assertThat(timeout.success()).isFalse();
        assertThat(timeout.errorMessage()).contains("超时");
        for (String value : Files.readAllLines(pidFile)) {
            long pid = Long.parseLong(value);
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        }

        String input = "{\"executable\":\"missing\",\"arguments\":[]}";
        assertThat(shell.tool().execute(input).success()).isFalse();
    }

    private static void awaitNonEmpty(Path file, CompletableFuture<?> execution) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while ((!Files.exists(file) || Files.size(file) == 0) && !execution.isDone()
                && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(file).exists();
        assertThat(Files.size(file)).isPositive();
    }

    @Test
    void rejectsUnknownFieldsTrailingJsonAndSkillWorkingDirectory() throws Exception {
        assertThat(shell.tool().execute("{\"executable\":\"java\",\"arguments\":[],\"extra\":1}")
                .success()).isFalse();
        assertThat(shell.tool().execute("{} {}").success()).isFalse();
        Path skill = Files.createDirectories(workspace.resolve("skills/news"));
        Files.writeString(skill.resolve("SKILL.md"), "---\nname: news\ndescription: 新闻\n---\n");
        Path bindings = Files.createDirectories(workspace.resolve("agents/shell-agent/skills"));
        Files.writeString(bindings.resolve("daily.link"), "../../../skills/news");
        ToolResult result = execute(List.of("echo", "no"), skill.toString(), 5);
        assertThat(result.success()).isFalse();
    }

    private ToolResult execute(List<String> helperArguments, String workingDirectory, Integer timeout) {
        var arguments = new java.util.ArrayList<String>();
        arguments.add("-cp");
        arguments.add(classpath);
        arguments.add(Helper.class.getName());
        arguments.addAll(helperArguments);
        var input = new StringBuilder("{\"executable\":\"java\",\"arguments\":")
                .append(org.springframework.ai.util.json.JsonParser.toJson(arguments));
        if (workingDirectory != null) {
            input.append(",\"working_directory\":")
                    .append(org.springframework.ai.util.json.JsonParser.toJson(workingDirectory));
        }
        if (timeout != null) {
            input.append(",\"timeout_seconds\":").append(timeout);
        }
        return shell.tool().execute(input.append('}').toString());
    }

    public static final class Helper {
        private Helper() {
        }

        public static void main(String[] arguments) throws Exception {
            switch (arguments[0]) {
                case "echo" -> System.out.print(String.join("|", java.util.Arrays.copyOfRange(
                        arguments, 1, arguments.length)));
                case "flood" -> {
                    byte[] block = "x".repeat(4096).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    for (int index = 0; index < 70; index++) {
                        System.out.write(block);
                        System.err.write(block);
                    }
                }
                case "sleep" -> {
                    Files.writeString(Path.of(arguments[1]), Long.toString(ProcessHandle.current().pid()));
                    Thread.sleep(30_000);
                }
                case "tree" -> {
                    String java = ProcessHandle.current().info().command().orElseThrow();
                    Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                            Helper.class.getName(), "sleep-child").start();
                    Files.writeString(Path.of(arguments[1]), ProcessHandle.current().pid() + "\n" + child.pid());
                    Thread.sleep(30_000);
                }
                case "sleep-child" -> Thread.sleep(30_000);
                default -> throw new IllegalArgumentException("unknown helper action");
            }
        }
    }
}
