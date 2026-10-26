package io.oryxos.provider.openai;

import java.time.Duration;

import io.oryxos.provider.ProviderDefinition;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

public final class OpenAiCompatibleChatModelFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";

    public ChatModel create(ProviderDefinition definition) {
        return create(definition, DEFAULT_TIMEOUT);
    }

    ChatModel create(ProviderDefinition definition, Duration timeout) {
        var api = OpenAiApi.builder()
                .baseUrl(baseUrl(definition.baseUrl(), DEFAULT_BASE_URL))
                .apiKey(definition.apiKey())
                .restClientBuilder(restClient(timeout))
                .build();
        var options = OpenAiChatOptions.builder()
                .model(definition.model())
                .internalToolExecutionEnabled(false)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
    }

    private static RestClient.Builder restClient(Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private static String baseUrl(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }
}
