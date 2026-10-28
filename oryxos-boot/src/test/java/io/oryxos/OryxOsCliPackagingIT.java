package io.oryxos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OryxOsCliPackagingIT {
    @TempDir
    Path workspace;

    @Test
    void executableJarExposesRootGroupsAndTwelveBusinessHelpPaths() throws Exception {
        List<List<String>> paths = List.of(
                List.of(), List.of("profile"), List.of("provider"), List.of("tool"), List.of("session"),
                List.of("init"), List.of("status"), List.of("chat"), List.of("serve"), List.of("gateway"),
                List.of("profile", "list"), List.of("profile", "create"), List.of("profile", "show"),
                List.of("profile", "delete"), List.of("provider", "list"), List.of("tool", "list"),
                List.of("session", "list"));
        for (List<String> path : paths) {
            var args = new ArrayList<>(path);
            args.add("--help");
            Result result = run(args);
            assertThat(result.exit()).as("help path %s", path).isZero();
            assertThat(result.output()).as("help path %s", path).contains("Usage:");
            assertThat(result.error()).as("help path %s", path).isEmpty();
        }
    }

    @Test
    void versionAndLightCommandsNeedNoProviderCredentialWhileUnknownCommandReturnsTwo()
            throws Exception {
        Result version = run(List.of("--version"));
        assertThat(version.exit()).isZero();
        assertThat(version.output()).contains("OryxOS 0.1.0-SNAPSHOT");

        Result init = run(List.of("init"));
        assertThat(init.exit()).isZero();
        assertThat(init.output()).contains("workspace=", "created=");
        Result status = run(List.of("status"));
        assertThat(status.exit()).isZero();
        assertThat(status.output()).contains("initialized=true");

        Result unknown = run(List.of("definitely-unknown"));
        assertThat(unknown.exit()).isEqualTo(2);
        assertThat(unknown.error()).isNotBlank();
    }

    @Test
    void onlyBootModuleProducesAnExecutableJar() throws Exception {
        assertThat(executableJar()).exists();
        Path cliTarget = Path.of("..", "oryxos-cli", "target").normalize();
        if (Files.isDirectory(cliTarget)) {
            try (var files = Files.list(cliTarget)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith("-exec.jar")))
                        .isEmpty();
            }
        }
    }

    private Result run(List<String> arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(javaExecutable().toString());
        command.add("-Doryxos.root=" + workspace.toAbsolutePath());
        command.add("-jar");
        command.add(executableJar().toString());
        command.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
        processBuilder.environment().remove("_JAVA_OPTIONS");
        processBuilder.environment().remove("JDK_JAVA_OPTIONS");
        Process process = processBuilder.start();
        boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new AssertionError("CLI 子进程超时: " + arguments);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.exitValue(), output, error);
    }

    private static Path executableJar() throws IOException {
        try (var files = Files.list(Path.of("target"))) {
            return files.filter(path -> path.getFileName().toString().matches("oryxos-boot-.+-exec\\.jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("未找到 Boot exec JAR"));
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private record Result(int exit, String output, String error) { }
}
