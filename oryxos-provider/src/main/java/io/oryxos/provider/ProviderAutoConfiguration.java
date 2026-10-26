package io.oryxos.provider;

import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.provider.dashscope.DashScopeChatModelFactory;
import io.oryxos.provider.openai.OpenAiCompatibleChatModelFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderAutoConfiguration {

    @Bean
    CredentialResolver credentialResolver() {
        return new CredentialResolver();
    }

    @Bean
    ProviderStartupValidator providerStartupValidator(CredentialResolver credentialResolver) {
        return new ProviderStartupValidator(credentialResolver);
    }

    @Bean
    DashScopeChatModelFactory dashScopeChatModelFactory() {
        return new DashScopeChatModelFactory();
    }

    @Bean
    OpenAiCompatibleChatModelFactory openAiCompatibleChatModelFactory() {
        return new OpenAiCompatibleChatModelFactory();
    }

    @Bean
    ProviderRegistry providerRegistry(ProviderProperties properties, ProfileRegistry profiles,
            CredentialResolver credentialResolver, ProviderStartupValidator validator,
            DashScopeChatModelFactory dashScopeFactory,
            OpenAiCompatibleChatModelFactory openAiFactory) {
        validator.validateAndPrune(profiles, properties);
        return ProviderRegistry.build(properties, profiles.referencedProviderNames(), credentialResolver,
                dashScopeFactory, openAiFactory);
    }

    @Bean
    ProviderService providerService(ProviderRegistry registry, LlmCallRecorder recorder) {
        return new DefaultProviderService(registry, recorder);
    }
}
