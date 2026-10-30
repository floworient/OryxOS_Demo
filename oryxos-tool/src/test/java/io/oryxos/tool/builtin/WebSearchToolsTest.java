package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.tool.search.SearchResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.JsonParser;

class WebSearchToolsTest {

    @Test
    void forwardsBoundedQueryAndLimitsProviderResultsAndOutput() {
        var requestedLimit = new AtomicInteger();
        var provider = new io.oryxos.tool.search.SearchProvider() {
            @Override
            public List<SearchResult> search(String query, int limit) {
                assertThat(query).isEqualTo("OryxOS Tool");
                requestedLimit.set(limit);
                var results = new ArrayList<SearchResult>();
                for (int index = 0; index < 20; index++) {
                    results.add(new SearchResult("标题".repeat(1_000),
                            URI.create("https://example.com/" + index), "摘要".repeat(10_000)));
                }
                return results;
            }
        };

        var result = new WebSearchTools(provider).tool().execute(
                "{\"query\":\"OryxOS Tool\",\"limit\":10}");

        assertThat(result.success()).isTrue();
        assertThat(requestedLimit).hasValue(10);
        assertThat(result.content().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64 * 1024);
        @SuppressWarnings("unchecked")
        var decoded = (java.util.Map<String, Object>) JsonParser.fromJson(result.content(), Object.class);
        assertThat((List<?>) decoded.get("results")).hasSizeLessThanOrEqualTo(10);
        assertThat(decoded.get("truncated")).isEqualTo(true);
    }

    @Test
    void rejectsInvalidInputAndMapsProviderFailureWithoutFakeResults() {
        var tools = new WebSearchTools((query, limit) -> {
            throw new IllegalStateException("canary-secret");
        });

        var failed = tools.tool().execute("{\"query\":\"news\"}");
        assertThat(failed.success()).isFalse();
        assertThat(failed.errorMessage()).contains("搜索失败").doesNotContain("canary-secret");
        assertThat(tools.tool().execute("{\"query\":\"news\",\"limit\":11}").success()).isFalse();
        assertThat(tools.tool().execute("{} {}").success()).isFalse();
    }
}
