package io.oryxos.tool.search;

import io.oryxos.tool.sandbox.HttpRequestAction;
import io.oryxos.tool.sandbox.Sandbox;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.util.json.JsonParser;

/** DuckDuckGo Instant Answer/Related Topics 适配器；它不是完整网页 SERP。 */
public final class DuckDuckGoInstantAnswerSearchProvider implements SearchProvider {

    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.duckduckgo.com/");

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final Sandbox sandbox;

    private final HttpClient client;

    private final URI endpoint;

    private final Duration timeout;

    public DuckDuckGoInstantAnswerSearchProvider(Sandbox sandbox) {
        this(sandbox, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), DEFAULT_ENDPOINT,
                Duration.ofSeconds(30));
    }

    public DuckDuckGoInstantAnswerSearchProvider(Sandbox sandbox, HttpClient client, URI endpoint,
            Duration timeout) {
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.client = java.util.Objects.requireNonNull(client);
        this.endpoint = java.util.Objects.requireNonNull(endpoint);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("搜索超时必须为正数");
        }
        this.timeout = timeout;
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank() || limit < 1 || limit > 10) {
            throw new IllegalArgumentException("搜索参数非法");
        }
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI requested = endpoint.resolve("?q=" + encoded
                + "&format=json&no_html=1&skip_disambig=1");
        URI target = sandbox.enforce(new HttpRequestAction(requested));
        var request = HttpRequest.newBuilder(target).timeout(timeout)
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                close(response.body());
                throw new IllegalStateException("搜索 Provider HTTP 状态异常");
            }
            byte[] body;
            try (InputStream stream = response.body()) {
                body = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (body.length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("搜索 Provider 响应超过上限");
            }
            return decode(new String(body, StandardCharsets.UTF_8), limit);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("搜索 Provider 请求已中断", exception);
        }
        catch (IOException exception) {
            throw new IllegalStateException("搜索 Provider 请求失败", exception);
        }
    }

    private static List<SearchResult> decode(String json, int limit) throws IOException {
        Object decoded = JsonParser.getObjectMapper().readerFor(Object.class).readValue(json);
        if (!(decoded instanceof Map<?, ?> root)) {
            throw new IllegalStateException("搜索 Provider 响应非法");
        }
        var results = new ArrayList<SearchResult>();
        add(results, text(root, "Heading"), text(root, "AbstractURL"), text(root, "AbstractText"), limit);
        collectTopics(root.get("RelatedTopics"), results, limit);
        return List.copyOf(results);
    }

    private static void collectTopics(Object raw, List<SearchResult> results, int limit) {
        if (!(raw instanceof List<?> topics)) {
            return;
        }
        for (Object topic : topics) {
            if (results.size() >= limit) {
                return;
            }
            if (topic instanceof Map<?, ?> values) {
                add(results, title(text(values, "Text")), text(values, "FirstURL"),
                        text(values, "Text"), limit);
                collectTopics(values.get("Topics"), results, limit);
            }
        }
    }

    private static void add(List<SearchResult> results, String title, String url, String snippet, int limit) {
        if (results.size() >= limit || url.isBlank() || snippet.isBlank()) {
            return;
        }
        try {
            URI target = URI.create(url);
            if (target.isAbsolute() && target.getHost() != null) {
                results.add(new SearchResult(title.isBlank() ? target.getHost() : title, target, snippet));
            }
        }
        catch (IllegalArgumentException ignored) {
            // 单条坏 URL 不应丢弃整个已验证响应。
        }
    }

    private static String title(String text) {
        int separator = text.indexOf(" - ");
        return separator > 0 ? text.substring(0, separator) : text;
    }

    private static String text(Map<?, ?> values, String field) {
        Object value = values.get(field);
        return value instanceof String text ? text : "";
    }

    private static void close(InputStream stream) {
        try {
            stream.close();
        }
        catch (IOException ignored) {
            // HTTP 错误响应只需释放连接。
        }
    }
}
