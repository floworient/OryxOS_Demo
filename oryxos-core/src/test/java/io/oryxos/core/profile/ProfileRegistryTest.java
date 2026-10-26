package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProfileRegistryTest {

    @Test
    void registersQueriesListsProvidersAndRemovesProfiles() {
        ProfileRegistry registry = new ProfileRegistry();
        Profile alpha = profile("alpha", "qwen");
        Profile beta = profile("beta", "gpt");

        registry.register(alpha);
        registry.register(beta);

        assertThat(registry.find("alpha")).contains(alpha);
        assertThat(registry.all()).containsExactlyInAnyOrder(alpha, beta).isUnmodifiable();
        assertThat(registry.referencedProviderNames()).containsExactlyInAnyOrder("qwen", "gpt");
        assertThat(registry.remove("alpha")).contains(alpha);
        assertThat(registry.find("alpha")).isEmpty();
    }

    @Test
    void duplicateNameIsRejected() {
        ProfileRegistry registry = new ProfileRegistry();
        registry.register(profile("alpha", "qwen"));

        assertThatThrownBy(() -> registry.register(profile("alpha", "gpt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alpha");
    }

    private static Profile profile(String name, String provider) {
        return new Profile(name, "description", new Profile.Identity(name, "prompt"),
                new Profile.ProviderRef(provider, "model", null), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(10, 20), Path.of(name, "AGENT.md"));
    }
}
