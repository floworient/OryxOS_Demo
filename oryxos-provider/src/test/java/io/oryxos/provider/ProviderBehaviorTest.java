package io.oryxos.provider;

import static io.oryxos.provider.DefaultProviderServiceChatTest.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallStatus;
import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class ProviderBehaviorTest {

    @Test
    void routesExactlyAndRejectsDuplicatesAndUnknownNames() {
        var alpha = StubChatModel.text("alpha", "a");
        var beta = StubChatModel.text("beta", "b");
        var definitions = List.of(definition("alpha"), definition("beta"));
        var service = new DefaultProviderService(
                new ProviderRegistry(definitions, Map.of("alpha", alpha, "beta", beta)), record -> record);

        assertThat(service.chat(null, profile("a", "alpha", "m-a", 0.2), new Prompt("x")).content())
                .isEqualTo("alpha");
        assertThat(service.chat(null, profile("b", "beta", "m-b", 0.4), new Prompt("x")).content())
                .isEqualTo("beta");
        assertThatThrownBy(() -> service.chat(null,
                profile("c", "missing", "m", null), new Prompt("x")))
                .isInstanceOfSatisfying(ProviderException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ProviderErrorCode.UNKNOWN_PROVIDER));
        assertThatThrownBy(() -> new ProviderRegistry(
                List.of(definition("alpha"), definition("alpha")), Map.of()))
                .isInstanceOfSatisfying(ProviderException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ProviderErrorCode.DUPLICATE_PROVIDER));
    }

    @Test
    void emptyResponseIsReturnedAndAuditedAsSuspicious() {
        var audit = new ArrayList<LlmCallRecord>();
        var service = new DefaultProviderService(
                new ProviderRegistry(List.of(definition("alpha")),
                        Map.of("alpha", StubChatModel.empty("m"))),
                record -> {
                    audit.add(record);
                    return record;
                });

        var response = service.chat(null, profile("a", "alpha", "m", null), new Prompt("x"));

        assertThat(response.content()).isBlank();
        assertThat(audit).singleElement().extracting(LlmCallRecord::status)
                .isEqualTo(LlmCallStatus.EMPTY_RESPONSE);
    }

    @Test
    void auditFailureClosesSuccessfulCall() {
        var service = new DefaultProviderService(
                new ProviderRegistry(List.of(definition("alpha")),
                        Map.of("alpha", StubChatModel.text("ok", "m"))),
                record -> {
                    throw new IllegalStateException("database unavailable");
                });

        assertThatThrownBy(() -> service.chat(null,
                profile("a", "alpha", "m", null), new Prompt("x")))
                .isInstanceOfSatisfying(ProviderException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(ProviderErrorCode.AUDIT_PERSISTENCE_FAILED));
    }

    private static ProviderDefinition definition(String name) {
        return new ProviderDefinition(name, ProviderDefinition.Type.DASHSCOPE, "m", null, "test-key");
    }
}
