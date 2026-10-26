package io.oryxos.provider.dashscope;

import java.time.Duration;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import io.oryxos.provider.ProviderDefinition;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

public final class DashScopeChatModelFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

    public ChatModel create(ProviderDefinition definition) {
        return create(definition, DEFAULT_TIMEOUT);
    }

    ChatModel create(ProviderDefinition definition, Duration timeout) {
        var api = DashScopeApi.builder()
                .baseUrl(baseUrl(definition.baseUrl(), DEFAULT_BASE_URL))
                .apiKey(definition.apiKey())
                .restClientBuilder(restClient(timeout))
                .build();
        var options = DashScopeChatOptions.builder()
                .model(definition.model())
                .internalToolExecutionEnabled(false)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
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
