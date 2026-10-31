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
        this.baseUrl = Objects.requireNonNull(baseUrl).resolve("/");
        this.apiKey = requireText(apiKey);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("Mem0 响应上限必须大于 0");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout)
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
            byte[] body = response.body().readNBytes(maxResponseBytes + 1);
            if (body.length > maxResponseBytes || response.statusCode() < 200 || response.statusCode() >= 300) {
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
