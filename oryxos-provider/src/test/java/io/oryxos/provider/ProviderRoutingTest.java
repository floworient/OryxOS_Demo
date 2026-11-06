package io.oryxos.provider;

import static io.oryxos.provider.DefaultProviderServiceChatTest.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.provider.ProviderCatalog;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class ProviderRoutingTest {

    @Test
    void exposesImmutableConfiguredProviderNamesThroughCoreContract() {
        ProviderCatalog catalog = new ProviderRegistry(
                List.of(definition("alpha"), definition("beta")), Map.of());

        assertThat(catalog.configuredNames()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(catalog.configuredNames()).isUnmodifiable();
    }

    @Test
    void providerServiceRemainsAFunctionalInterface() {
        ProviderService service = (sessionId, profile, prompt) -> null;

        assertThat(service).isNotNull();
    }

    @Test
    void twoProvidersAndAgentsNeverCrossRouteAndAuditActualNames() {
        var audit = new ArrayList<LlmCallRecord>();
        var definitions = List.of(definition("alpha"), definition("beta"));
        var service = new DefaultProviderService(new ProviderRegistry(definitions, Map.of(
                "alpha", StubChatModel.text("alpha", "a"),
                "beta", StubChatModel.text("beta", "b"))), record -> {
                    audit.add(record);
                    return record;
                });

        assertThat(service.chat(null, profile("agent-a", "alpha", "model-a", null),
                new Prompt("x")).content()).isEqualTo("alpha");
        assertThat(service.chat(null, profile("agent-b", "beta", "model-b", null),
                new Prompt("x")).content()).isEqualTo("beta");
        assertThat(audit).extracting(LlmCallRecord::profileName, LlmCallRecord::provider,
                LlmCallRecord::model).containsExactly(
                        org.assertj.core.groups.Tuple.tuple("agent-a", "alpha", "model-a"),
                        org.assertj.core.groups.Tuple.tuple("agent-b", "beta", "model-b"));
    }

    private static ProviderDefinition definition(String name) {
        return new ProviderDefinition(name, ProviderDefinition.Type.DASHSCOPE, "m", null, "key");
    }
}
