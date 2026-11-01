package io.oryxos.memory.mem0;

import io.oryxos.core.memory.MemoryException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class Mem0Client {
    private final URI baseUrl;

    private final String apiKey;

    private final Duration requestTimeout;

    private final int maxResponseBytes;

    private final HttpClient http;

    public Mem0Client(URI baseUrl, String apiKey, Duration connectTimeout,
            Duration requestTimeout, int maxResponseBytes) {
        this.baseUrl = requireBaseUrl(baseUrl);
        this.apiKey = requireText(apiKey);
        this.requestTimeout = requirePositive(requestTimeout, "Mem0 请求超时");
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("Mem0 响应上限必须大于 0");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.http = HttpClient.newBuilder()
                .connectTimeout(requirePositive(connectTimeout, "Mem0 连接超时"))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    String get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    String post(String path, String json) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
    }

    private String send(HttpRequest request) {
        var builder = HttpRequest.newBuilder(request.uri()).timeout(requestTimeout)
                .header("X-API-Key", apiKey)
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        request.headers().map().forEach((name, values) ->
                values.forEach(value -> builder.header(name, value)));
        var authenticated = builder.build();
        try {
            HttpResponse<java.io.InputStream> response = http.send(authenticated,
                    HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (var stream = response.body()) {
                body = stream.readNBytes(maxResponseBytes + 1);
            }
            String mediaType = response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
            boolean json = mediaType.equals("application/json") || mediaType.endsWith("+json");
            if (body.length > maxResponseBytes || response.statusCode() < 200
                    || response.statusCode() >= 300 || !json) {
                throw unavailable("Mem0 请求失败");
            }
            return new String(body, StandardCharsets.UTF_8);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("Mem0 请求已中断");
        }
        catch (IOException | RuntimeException exception) {
            if (exception instanceof MemoryException memory) {
                throw memory;
            }
            throw unavailable("Mem0 服务不可用");
        }
    }

    private URI uri(String path) {
        return baseUrl.resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    private static URI requireBaseUrl(URI value) {
        URI normalized = Objects.requireNonNull(value, "Mem0 base URL 不能为空").normalize();
        String scheme = normalized.getScheme();
        String path = normalized.getPath();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || normalized.getHost() == null || normalized.getUserInfo() != null
                || normalized.getQuery() != null || normalized.getFragment() != null
                || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException("Mem0 base URL 必须是无路径、凭证、查询和片段的 HTTP(S) 地址");
        }
        return normalized.resolve("/");
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "必须大于 0");
        }
        return value;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mem0 API key 不能为空");
        }
        return value;
    }

    private static MemoryException unavailable(String message) {
        return new MemoryException(MemoryException.Code.STORE_UNAVAILABLE, message);
    }
}
