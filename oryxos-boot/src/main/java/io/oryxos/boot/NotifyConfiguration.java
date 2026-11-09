package io.oryxos.boot;

import io.oryxos.core.notify.NotifyChannelRegistry;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.tool.builtin.NotifyTools;
import io.oryxos.tool.notify.NotifyAdapterRegistry;
import io.oryxos.tool.notify.NotifyChannelAdapter;
import io.oryxos.tool.notify.NotifyTargetResolver;
import io.oryxos.tool.notify.WebhookNotifyAdapter;
import io.oryxos.tool.sandbox.Sandbox;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class NotifyConfiguration {

    @Bean
    NotifyChannelService notifyChannelService(NotifyChannelRegistry registry) {
        return new NotifyChannelService(registry, Clock.systemUTC());
    }

    @Bean
    RestClient notifyRestClient() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    NotifyChannelAdapter webhookNotifyAdapter(RestClient notifyRestClient) {
        return new WebhookNotifyAdapter(notifyRestClient);
    }

    @Bean
    NotifyAdapterRegistry notifyAdapterRegistry(List<NotifyChannelAdapter> adapters) {
        return new NotifyAdapterRegistry(adapters);
    }

    @Bean
    NotifyTargetResolver notifyTargetResolver(Sandbox notifySandbox) {
        return new NotifyTargetResolver(System::getenv, notifySandbox);
    }

    @Bean
    NotifyTools notifyTools(NotifyChannelService channels, NotifyTargetResolver resolver,
            NotifyAdapterRegistry adapters) {
        return new NotifyTools(channels, resolver, adapters);
    }
}
