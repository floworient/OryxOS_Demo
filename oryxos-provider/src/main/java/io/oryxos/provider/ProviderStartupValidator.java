package io.oryxos.provider;

import java.util.Map;

import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProviderStartupValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderStartupValidator.class);

    private final CredentialResolver credentialResolver;

    public ProviderStartupValidator(CredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    public void validateAndPrune(ProfileRegistry profiles, ProviderProperties properties) {
        var definitions = properties.providers();
        var shapeRegistry = new ProviderRegistry(definitions, Map.of());
        for (var profile : profiles.all()) {
            if (!shapeRegistry.configuredNames().contains(profile.provider().name())) {
                profiles.remove(profile.name());
                LOGGER.error("Agent Provider 引用无效 agentName={} field=provider.name reason=未配置供应商 {}",
                        profile.name(), profile.provider().name());
            }
        }
        var referenced = profiles.referencedProviderNames();
        for (var definition : definitions) {
            try {
                credentialResolver.resolve(definition.name(), definition.apiKey());
            }
            catch (ProviderException exception) {
                if (referenced.contains(definition.name())) {
                    throw exception;
                }
                LOGGER.warn("未引用的供应商凭据不可用 providerName={} field=api-key",
                        definition.name());
            }
        }
    }
}
