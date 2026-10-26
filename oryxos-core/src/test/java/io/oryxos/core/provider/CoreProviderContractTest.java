package io.oryxos.core.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CoreProviderContractTest {

    @Test
    void unknownUsageAndToolIntentRemainExplicitData() {
        TokenUsage usage = TokenUsage.UNKNOWN;
        ToolCallIntent intent = new ToolCallIntent("call-1", "weather", "{\"city\":\"杭州\"}");
        ProviderResponse response = new ProviderResponse("qwen", "qwen-plus", "", usage, List.of(intent), 12);

        assertThat(usage.isKnown()).isFalse();
        assertThat(response.toolCalls()).containsExactly(intent);
        assertThat(response.providerName()).isEqualTo("qwen");
    }

    @Test
    void providerExceptionExposesSafeStructuredContext() {
        ProviderException exception = new ProviderException(
                ProviderErrorCode.AUTH_FAILED, "qwen", "qwen-plus", "认证失败", null);

        assertThat(exception.errorCode()).isEqualTo(ProviderErrorCode.AUTH_FAILED);
        assertThat(exception.providerName()).isEqualTo("qwen");
        assertThat(exception.getMessage()).doesNotContain("api-key");
    }
}
