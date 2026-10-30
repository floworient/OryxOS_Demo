package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.interaction.UserInteractionContext;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InteractionToolsTest {

    @AfterEach
    void clearContext() {
        UserInteractionContext.clear();
    }

    @Test
    void asksExactlyOnceAndReturnsOneLineAnswer() {
        var calls = new AtomicInteger();
        UserInteractionContext.set(question -> {
            calls.incrementAndGet();
            assertThat(question).isEqualTo("请选择环境");
            return "生产环境";
        });

        var result = new InteractionTools().tool().execute("{\"question\":\"请选择环境\"}");

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("生产环境");
        assertThat(calls).hasValue(1);
    }

    @Test
    void unattendedInvocationFailsImmediatelyAndMalformedInputNeverAsks() {
        assertThat(Duration.ofNanos(measure(() -> {
            var result = new InteractionTools().tool().execute("{\"question\":\"继续吗\"}");
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("不支持交互");
        }))).isLessThan(Duration.ofSeconds(1));

        var calls = new AtomicInteger();
        UserInteractionContext.set(question -> {
            calls.incrementAndGet();
            return "不应调用";
        });
        assertThat(new InteractionTools().tool().execute(
                "{\"question\":\"问题\",\"unknown\":true}").success()).isFalse();
        assertThat(calls).hasValue(0);
    }

    private static long measure(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return System.nanoTime() - started;
    }
}
