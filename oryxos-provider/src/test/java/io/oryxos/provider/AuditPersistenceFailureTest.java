package io.oryxos.provider;

import static io.oryxos.provider.DefaultProviderServiceChatTest.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;

class AuditPersistenceFailureTest {

    @Test
    void recorderFailureOverridesBothModelSuccessAndFailure() {
        assertAuditFailure(StubChatModel.text("ok", "m"));
        assertAuditFailure(StubChatModel.failure(
                new ResourceAccessException("network", new ConnectException())));
    }

    private static void assertAuditFailure(StubChatModel model) {
        var definition = new ProviderDefinition("alpha", ProviderDefinition.Type.DASHSCOPE,
                "m", null, "key");
        var service = new DefaultProviderService(new ProviderRegistry(List.of(definition),
                Map.of("alpha", model)), record -> {
                    throw new IllegalStateException("database unavailable");
                });
        assertThatThrownBy(() -> service.chat(null, profile("a", "alpha", "m", null),
                new Prompt("x"))).isInstanceOfSatisfying(ProviderException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ProviderErrorCode.AUDIT_PERSISTENCE_FAILED);
                    assertThat(error.getCause()).isInstanceOf(IllegalStateException.class);
                });
    }
}
