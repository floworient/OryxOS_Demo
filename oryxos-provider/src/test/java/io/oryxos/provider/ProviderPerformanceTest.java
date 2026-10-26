package io.oryxos.provider;

import static io.oryxos.provider.DefaultProviderServiceChatTest.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class ProviderPerformanceTest {

    @Test
    void immediateStubProviderInternalP95IsAtMostFiftyMilliseconds() {
        var definition = new ProviderDefinition("alpha", ProviderDefinition.Type.DASHSCOPE,
                "m", null, "key");
        var service = new DefaultProviderService(new ProviderRegistry(List.of(definition),
                Map.of("alpha", StubChatModel.text("ok", "m"))), record -> record);
        var samples = new long[100];
        for (int index = 0; index < samples.length; index++) {
            var started = System.nanoTime();
            service.chat(null, profile("a", "alpha", "m", 0.2), new Prompt("x"));
            samples[index] = System.nanoTime() - started;
        }
        Arrays.sort(samples);
        var p95Millis = samples[94] / 1_000_000.0;
        assertThat(p95Millis).isLessThanOrEqualTo(50.0);
    }
}
