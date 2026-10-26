package io.oryxos.provider;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oryxos")
public record ProviderProperties(List<ProviderDefinition> providers) {

    public ProviderProperties {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
