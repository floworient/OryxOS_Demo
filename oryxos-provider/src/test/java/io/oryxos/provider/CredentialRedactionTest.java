package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oryxos.core.provider.ProviderException;
import org.junit.jupiter.api.Test;

class CredentialRedactionTest {

    @Test
    void sentinelSecretDoesNotAppearInDefinitionOrFailure() {
        var secret = "SENTINEL-SECRET-123";
        var definition = new ProviderDefinition("alpha", ProviderDefinition.Type.DASHSCOPE,
                "m", null, secret);
        assertThat(definition.toString()).doesNotContain(secret);
        assertThatCode(() -> assertThat(new ObjectMapper().writeValueAsString(definition))
                .doesNotContain(secret)
                .doesNotContain("apiKey"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> new CredentialResolver(name -> null)
                .resolve("alpha", "${ALPHA_KEY}"))
                .isInstanceOfSatisfying(ProviderException.class, error -> {
                    assertThat(error.getMessage()).doesNotContain(secret);
                    assertThat(error.toString()).doesNotContain(secret);
                });
    }
}
