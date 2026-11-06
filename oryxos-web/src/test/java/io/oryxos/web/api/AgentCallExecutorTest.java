package io.oryxos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentCallExecutorTest {

    private final java.util.concurrent.ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void returnsSuccessfulResultAndPreservesProviderException() {
        var callExecutor = new AgentCallExecutor(executor, Duration.ofSeconds(1));
        var providerFailure = new ProviderException(ProviderErrorCode.NETWORK_ERROR,
                "provider", "model", "secret upstream detail", null);

        assertThat(callExecutor.execute(() -> "reply")).isEqualTo("reply");
        assertThatThrownBy(() -> callExecutor.execute(() -> {
            throw providerFailure;
        })).isSameAs(providerFailure);
    }

    @Test
    void cancelsAndInterruptsTimedOutCallWithSanitizedException() throws Exception {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var callExecutor = new AgentCallExecutor(executor, Duration.ofMillis(50));

        assertThatThrownBy(() -> callExecutor.execute(() -> {
            entered.countDown();
            try {
                Thread.sleep(Duration.ofSeconds(30));
            }
            catch (InterruptedException exception) {
                interrupted.countDown();
                throw new IllegalStateException("canary-secret", exception);
            }
            return "late";
        })).isInstanceOf(AgentCallTimeoutException.class)
                .hasMessage("Agent 调用超时")
                .hasMessageNotContaining("canary-secret");

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
