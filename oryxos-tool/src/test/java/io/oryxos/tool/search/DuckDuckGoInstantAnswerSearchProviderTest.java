package io.oryxos.tool.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DuckDuckGoInstantAnswerSearchProviderTest {

    @Test
    @SuppressWarnings("unchecked")
    void enforcesSandboxAndMapsInstantAnswerAndNestedTopics() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream("""
                {"Heading":"OryxOS","AbstractURL":"https://example.com/main","AbstractText":"摘要",
                 "RelatedTopics":[{"Text":"主题一 - 说明","FirstURL":"https://example.com/one"},
                   {"Name":"分组","Topics":[{"Text":"主题二","FirstURL":"https://example.com/two"}]}]}
                """.getBytes(StandardCharsets.UTF_8)));
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<java.io.InputStream>>any()))
                .thenReturn(response);
        var enforced = new AtomicReference<URI>();
        io.oryxos.tool.sandbox.Sandbox sandbox = action -> {
            enforced.set(action.target());
            return action.target();
        };
        var provider = new DuckDuckGoInstantAnswerSearchProvider(sandbox, client,
                URI.create("https://api.duckduckgo.com/"), Duration.ofSeconds(2));

        var results = provider.search("OryxOS Tool", 3);

        assertThat(enforced.get().getHost()).isEqualTo("api.duckduckgo.com");
        assertThat(enforced.get().getRawQuery()).contains("q=OryxOS+Tool", "format=json");
        assertThat(results).extracting(SearchResult::title)
                .containsExactly("OryxOS", "主题一", "主题二");
    }
}
