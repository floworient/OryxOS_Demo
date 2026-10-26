package io.oryxos.provider;

import static io.oryxos.provider.DefaultProviderServiceChatTest.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallStatus;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class EmptyResponseAndUnknownUsageTest {

    @Test
    void toolOnlyResponseKeepsUnknownUsageAndEmptyStatus() {
        var audit = new ArrayList<LlmCallRecord>();
        var definition = new ProviderDefinition("alpha", ProviderDefinition.Type.DASHSCOPE,
                "m", null, "key");
        var service = new DefaultProviderService(new ProviderRegistry(List.of(definition),
                Map.of("alpha", StubChatModel.toolCall("m"))), record -> {
                    audit.add(record);
                    return record;
                });

        var response = service.chat(null, profile("a", "alpha", "m", null), new Prompt("x"));

        assertThat(response.usage().promptTokens()).isNull();
        assertThat(response.usage().completionTokens()).isNull();
        assertThat(response.usage().totalTokens()).isNull();
        assertThat(audit).singleElement().satisfies(record -> {
            assertThat(record.usageKnown()).isFalse();
            assertThat(record.status()).isEqualTo(LlmCallStatus.EMPTY_RESPONSE);
        });
    }
}
