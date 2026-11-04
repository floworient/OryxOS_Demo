package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.core.tool.ToolSource;
import io.oryxos.tool.registry.DefaultToolRegistry;
import io.oryxos.tool.sandbox.FileAccessAction;
import io.oryxos.tool.sandbox.HttpRequestAction;
import io.oryxos.tool.sandbox.Sandbox;
import io.oryxos.tool.sandbox.WhitelistSandbox;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

class FileToolsCancellationTest {

    @TempDir
    Path workspace;

    @ParameterizedTest
    @ValueSource(strings = {"grep", "glob", "list_dir"})
    void executorTimeoutStopsFurtherFileAccessAfterInFlightCheckReturns(String name) throws Exception {
        Path agent = Files.createDirectories(workspace.resolve("agents/reader"));
        Path source = Files.writeString(agent.resolve("AGENT.md"), "---\nname: reader\n---\n测试");
        Path directory = Files.createDirectories(agent.resolve("data"));
        Files.writeString(directory.resolve("a.txt"), "needle");
        Files.writeString(directory.resolve("b.txt"), "needle");
        var profile = new Profile("reader", "测试", new Profile.Identity("助手", "测试"),
                new Profile.ProviderRef("stub", "stub", null), List.of(name), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(3, 10), source);
        var delegate = new WhitelistSandbox(Set.of(), Map.of(), new SkillBindingResolver(workspace));
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var blocked = new AtomicBoolean();
        var laterChecks = new AtomicInteger();
        Sandbox observed = new Sandbox() {
            @Override
            public URI enforce(HttpRequestAction action) {
                return delegate.enforce(action);
            }

            @Override
            public Path enforce(FileAccessAction action) {
                boolean isFile = action.requested().getFileName().toString().endsWith(".txt");
                if (isFile && blocked.compareAndSet(false, true)) {
                    entered.countDown();
                    awaitReleaseKeepingInterrupt(release, interrupted);
                }
                else if (Thread.currentThread().isInterrupted()) {
                    laterChecks.incrementAndGet();
                }
                return delegate.enforce(action);
            }
        };
        OryxTool operation = new FileTools(observed).all().stream()
                .filter(tool -> tool.getName().equals(name)).findFirst().orElseThrow();
        OryxTool tracked = new OryxTool() {
            @Override
            public String getName() {
                return operation.getName();
            }

            @Override
            public String getDescription() {
                return operation.getDescription();
            }

            @Override
            public String getInputSchema() {
                return operation.getInputSchema();
            }

            @Override
            public ToolResult execute(String input) {
                try {
                    return operation.execute(input);
                }
                finally {
                    stopped.countDown();
                }
            }
        };
        var registry = new DefaultToolRegistry();
        registry.register(new ToolRegistration(tracked, ToolSource.BUILTIN, "builtin"));
        var records = new java.util.concurrent.CopyOnWriteArrayList<io.oryxos.core.audit.ToolInvocationRecord>();
        try (var executor = new ToolExecutor(registry, (p, call) -> { }, records::add,
                Executors.newVirtualThreadPerTaskExecutor(), Duration.ofSeconds(2), millis -> { })) {
            String pattern = name.equals("grep") ? "needle" : "*";
            String json = name.equals("list_dir") ? "{\"path\":\"data\"}"
                    : "{\"path\":\"data\",\"pattern\":\"" + pattern + "\"}";
            var result = CompletableFuture.supplyAsync(() -> {
                ProfileContext.set(profile);
                try {
                    return executor.execute("cancel-" + name, new ToolCallIntent("call", name, json));
                }
                finally {
                    ProfileContext.clear();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get(5, TimeUnit.SECONDS).errorMessage()).isEqualTo("工具执行超时");
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(laterChecks.get()).as("取消后不得继续授权/访问后续文件").isZero();
            assertThat(records).hasSize(1);
        }
        finally {
            release.countDown();
        }
    }

    private static void awaitReleaseKeepingInterrupt(CountDownLatch release, CountDownLatch observed) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    release.await();
                    return;
                }
                catch (InterruptedException exception) {
                    interrupted = true;
                    observed.countDown();
                }
            }
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
