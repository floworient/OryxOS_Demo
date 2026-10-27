package io.oryxos.core.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.oryxos.core.audit.ToolInvocationRecord;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.provider.ToolCallIntent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ToolExecutorTest {

    private final List<ToolInvocationRecord> records = new CopyOnWriteArrayList<>();

    private final AtomicInteger calls = new AtomicInteger();

    private final ToolCallIntent intent = new ToolCallIntent("c", "lookup", "{}");

    private Profile profile() {
        var profile = mock(Profile.class);
        when(profile.tools()).thenReturn(List.of("lookup"));
        ProfileContext.set(profile);
        return profile;
    }

    private OryxTool tool(java.util.function.Supplier<ToolResult> action) {
        return new OryxTool() {
            public String getName() {
                return "lookup";
            }

            public String getDescription() {
                return "查询";
            }

            public String getInputSchema() {
                return "{\"type\":\"object\"}";
            }

            public ToolResult execute(String input) {
                calls.incrementAndGet();
                return action.get();
            }
        };
    }

    @AfterEach
    void clear() {
        ProfileContext.clear();
    }

    @Test
    void successExecutesOncePropagatesContextAndPersistsAudit() throws Exception {
        var current = profile();
        var worker = Executors.newSingleThreadExecutor();
        try (var executor = new ToolExecutor(Map.of("lookup", tool(() -> {
            assertThat(ProfileContext.current()).isSameAs(current);
            return new ToolResult(true, "晴", null, false);
        })), (p, call) -> { }, records::add, worker, Duration.ofSeconds(2), millis -> { })) {
            assertThat(executor.execute("s", intent).content()).isEqualTo("晴");
            assertThat(calls).hasValue(1);
            assertThat(records).singleElement().satisfies(record -> {
                assertThat(record.sessionId()).isEqualTo("s");
                assertThat(record.toolName()).isEqualTo("lookup");
                assertThat(record.inputJson()).isEqualTo("{}");
                assertThat(record.resultJson()).contains("晴");
                assertThat(record.success()).isTrue();
                assertThat(record.durationMs()).isNotNegative();
                assertThat(record.createdAt()).isNotNull();
            });
            assertThat(worker.submit(ProfileContext::current).get()).isNull();
        }
    }

    @Test
    void unavailableInvisibleInvalidAndDeniedToolsNeverExecute() {
        var lookup = tool(() -> new ToolResult(true, "ok", null, false));
        try (var executor = new ToolExecutor(Map.of("lookup", lookup),
                (p, call) -> { throw new SecurityException("secret-token"); }, records::add)) {
            assertThat(executor.execute("s", intent).success()).isFalse();
            profile();
            assertThat(executor.execute("s", new ToolCallIntent("x", "unknown", "{}")).success()).isFalse();
            assertThat(executor.execute("s", new ToolCallIntent("x", "lookup", "[]")).success()).isFalse();
            assertThat(executor.execute("s", new ToolCallIntent("x", "lookup", "bad")).success()).isFalse();
            var invisible = mock(Profile.class);
            when(invisible.tools()).thenReturn(List.of());
            ProfileContext.set(invisible);
            assertThat(executor.execute("s", intent).success()).isFalse();
            profile();
            assertThat(executor.execute("s", intent).success()).isFalse();
        }
        assertThat(calls).hasValue(0);
        assertThat(records).hasSize(6).allSatisfy(record -> {
            assertThat(record.success()).isFalse();
            assertThat(record.errorMessage()).isNotBlank().doesNotContain("secret-token");
        });
    }

    @Test
    void retryableFailureHasFourAttemptsAndExponentialBackoff() {
        profile();
        var waits = new ArrayList<Long>();
        try (var executor = new ToolExecutor(Map.of("lookup",
                tool(() -> new ToolResult(false, "", "暂时不可用", true))),
                (p, call) -> { }, records::add, Executors.newVirtualThreadPerTaskExecutor(),
                Duration.ofSeconds(2), waits::add)) {
            assertThat(executor.execute("s", intent).success()).isFalse();
        }
        assertThat(calls).hasValue(4);
        assertThat(records).hasSize(4);
        assertThat(waits).containsExactly(100L, 200L, 400L);
    }

    @Test
    void thrownFailuresAreAuditedWithoutSecretOrRetry() {
        profile();
        try (var executor = new ToolExecutor(Map.of("lookup",
                tool(() -> { throw new IllegalStateException("secret-token"); })),
                (p, call) -> { }, records::add)) {
            assertThat(executor.execute("s", intent).errorMessage()).doesNotContain("secret-token");
        }
        assertThat(calls).hasValue(1);
        assertThat(records).singleElement().satisfies(record -> assertThat(record.success()).isFalse());
    }

    @Test
    void nullOrBlankToolNameIsAuditedAsFailureInsteadOfAbortingTheTurn() {
        profile();
        try (var executor = new ToolExecutor(Map.of("lookup",
                tool(() -> new ToolResult(true, "ok", null, false))), (p, call) -> { }, records::add)) {
            assertThat(executor.execute("s", new ToolCallIntent("x", null, "{}")).success()).isFalse();
            assertThat(executor.execute("s", new ToolCallIntent("x", "  ", "{}")).success()).isFalse();
        }
        assertThat(calls).hasValue(0);
        assertThat(records).hasSize(2).allSatisfy(record -> {
            assertThat(record.success()).isFalse();
            assertThat(record.toolName()).isNotBlank();
            assertThat(record.errorMessage()).isNotBlank();
        });
    }

    @Test
    void trailingJsonTokensAreRejectedBeforeToolExecution() {
        profile();
        try (var executor = new ToolExecutor(Map.of("lookup",
                tool(() -> new ToolResult(true, "ok", null, false))), (p, call) -> { }, records::add)) {
            assertThat(executor.execute("s", new ToolCallIntent("x", "lookup", "{} {}" )).success()).isFalse();
        }
        assertThat(calls).hasValue(0);
        assertThat(records).singleElement().satisfies(record -> assertThat(record.success()).isFalse());
    }

    @Test
    void auditFailureIsNotSwallowedOrRetried() {
        profile();
        ToolInvocationRecorder failing = record -> { throw new IllegalStateException("db down"); };
        try (var executor = new ToolExecutor(Map.of("lookup",
                tool(() -> new ToolResult(true, "ok", null, false))), (p, call) -> { }, failing)) {
            assertThatThrownBy(() -> executor.execute("s", intent))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("审计");
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void timedOutCallIsCancelledAndNeverRetried() throws Exception {
        profile();
        var started = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var lookup = tool(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                return new ToolResult(true, "", null, false);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new ToolResult(false, "", "取消", true);
            }
            finally {
                stopped.countDown();
            }
        });
        try (var executor = new ToolExecutor(Map.of("lookup", lookup), (p, call) -> { }, records::add,
                Executors.newVirtualThreadPerTaskExecutor(), Duration.ofMillis(300), millis -> { })) {
            assertThat(executor.execute("s", intent).errorMessage()).contains("超时");
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(stopped.await(2, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(calls).hasValue(1);
        assertThat(records).singleElement().satisfies(record -> assertThat(record.success()).isFalse());
    }
}
