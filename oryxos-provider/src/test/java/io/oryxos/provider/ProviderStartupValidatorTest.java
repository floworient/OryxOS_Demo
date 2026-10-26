package io.oryxos.provider;

import static io.oryxos.provider.DefaultProviderServiceChatTest.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import org.junit.jupiter.api.Test;

class ProviderStartupValidatorTest {

    @Test
    void removesOnlyProfilesThatReferenceUnknownProvider() {
        var profiles = new ProfileRegistry();
        profiles.register(profile("valid", "alpha", "m", null));
        profiles.register(profile("invalid", "missing", "m", null));
        var properties = new ProviderProperties(List.of(definition("alpha", "literal-key")));

        new ProviderStartupValidator(new CredentialResolver(name -> null))
                .validateAndPrune(profiles, properties);

        assertThat(profiles.find("valid")).isPresent();
        assertThat(profiles.find("invalid")).isEmpty();
    }

    @Test
    void referencedMissingCredentialFailsButUnreferencedOneDoesNot() {
        var profiles = new ProfileRegistry();
        profiles.register(profile("valid", "alpha", "m", null));
        var properties = new ProviderProperties(List.of(
                definition("alpha", "${ALPHA_KEY}"), definition("unused", "${UNUSED_KEY}")));
        var validator = new ProviderStartupValidator(
                new CredentialResolver(name -> "ALPHA_KEY".equals(name) ? "resolved" : null));

        validator.validateAndPrune(profiles, properties);

        var failingProfiles = new ProfileRegistry();
        failingProfiles.register(profile("invalid", "unused", "m", null));
        assertThatThrownBy(() -> validator.validateAndPrune(failingProfiles, properties))
                .isInstanceOfSatisfying(ProviderException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ProviderErrorCode.MISSING_CREDENTIAL));
    }

    @Test
    void credentialMessagesAndDefinitionNeverExposeSecret() {
        var secret = "SENTINEL-DO-NOT-LEAK";
        var definition = definition("alpha", secret);
        assertThat(definition.toString()).doesNotContain(secret);

        var error = new CredentialResolver(name -> secret)
                .resolve("alpha", "${ALPHA_KEY}");
        assertThat(error).isEqualTo(secret);
    }

    @Test
    void rejectsOpaqueOrHostlessHttpBaseUrlsBeforeModelConstruction() {
        for (String baseUrl : List.of("http:opaque", "https:/missing-host")) {
            var invalid = new ProviderDefinition("alpha", ProviderDefinition.Type.OPENAI_COMPATIBLE,
                    "m", baseUrl, "key");

            assertThatThrownBy(() -> new ProviderRegistry(List.of(invalid), Map.of()))
                    .isInstanceOfSatisfying(ProviderException.class, error -> {
                        assertThat(error.errorCode()).isEqualTo(ProviderErrorCode.INVALID_CONFIG);
                        assertThat(error.getMessage()).contains("base-url");
                    });
        }
    }

    private static ProviderDefinition definition(String name, String apiKey) {
        return new ProviderDefinition(name, ProviderDefinition.Type.DASHSCOPE, "m", null, apiKey);
    }
}
