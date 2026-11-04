package io.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.tool.sandbox.HttpRequestAction;
import io.oryxos.tool.sandbox.Sandbox;
import io.oryxos.tool.sandbox.SandboxViolationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.util.json.JsonParser;

public final class HttpTools {

    private static final int MAX_INPUT_BYTES = 256 * 1024;

    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "host",
            "connection", "proxy-connection", "keep-alive", "transfer-encoding", "te",
            "trailer", "upgrade");

    private static final Set<String> ALLOWED_HEADERS = Set.of("accept", "accept-language", "content-type",
            "user-agent");

    private static final String GET_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,"
            + "\"properties\":{\"url\":{\"type\":\"string\",\"minLength\":1},"
            + "\"headers\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}},"
            + "\"required\":[\"url\"]}";

    private static final String POST_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,"
            + "\"properties\":{\"url\":{\"type\":\"string\",\"minLength\":1},"
            + "\"body\":{\"type\":\"string\"},"
            + "\"headers\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}},"
            + "\"content_type\":{\"type\":\"string\",\"minLength\":1}},"
            + "\"required\":[\"url\",\"body\"]}";

    private final Sandbox sandbox;

    private final HttpClient client;

    private final Duration timeout;

    private final int maxResponseBytes;

    private final List<OryxTool> tools;

    public HttpTools(Sandbox sandbox) {
        this(sandbox, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), Duration.ofSeconds(60), 1024 * 1024);
    }

    public HttpTools(Sandbox sandbox, HttpClient client, Duration timeout, int maxResponseBytes) {
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.client = java.util.Objects.requireNonNull(client);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || maxResponseBytes <= 0) {
            throw new IllegalArgumentException("HTTP 工具限制必须为正数");
        }
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
        this.tools = List.of(
                tool("http_get", "向 HTTP 白名单内的地址发送单次 GET 请求", GET_SCHEMA, "GET"),
                tool("http_post", "向 HTTP 白名单内的地址发送单次 POST 请求", POST_SCHEMA, "POST"));
    }

    public List<OryxTool> all() {
        return tools;
    }

    private ToolResult execute(String method, String json) {
        try {
            RequestInput input = parse(method, json);
            URI target = sandbox.enforce(new HttpRequestAction(URI.create(input.url())));
            var builder = HttpRequest.newBuilder(target).timeout(timeout);
            input.headers().forEach(builder::header);
            if (method.equals("POST")) {
                String contentType = input.contentType() == null ? "application/json" : input.contentType();
                builder.setHeader("Content-Type", contentType);
                builder.POST(HttpRequest.BodyPublishers.ofString(input.body(), StandardCharsets.UTF_8));
            }
            else {
                builder.GET();
            }
            HttpResponse<InputStream> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            boolean truncated;
            try (InputStream stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return failed("HTTP 返回非成功状态: " + response.statusCode());
                }
                byte[] received = stream.readNBytes(maxResponseBytes + 1);
                truncated = received.length > maxResponseBytes;
                body = truncated ? java.util.Arrays.copyOf(received, maxResponseBytes) : received;
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("status", response.statusCode());
            result.put("body", new String(body, StandardCharsets.UTF_8));
            result.put("body_truncated", truncated);
            return new ToolResult(true, JsonParser.toJson(result), null, false);
        }
        catch (HttpTimeoutException exception) {
            return failed("HTTP 请求超时");
        }
        catch (SandboxViolationException exception) {
            return failed(exception.getMessage());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed("HTTP 请求已中断");
        }
        catch (RuntimeException | IOException exception) {
            return failed("HTTP 请求失败");
        }
    }

    private static RequestInput parse(String method, String json) throws IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("HTTP 输入超过上限");
        }
        Object decoded = JsonParser.getObjectMapper().readerFor(Object.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
        Set<String> allowed = method.equals("POST")
                ? Set.of("url", "body", "headers", "content_type") : Set.of("url", "headers");
        Set<String> required = method.equals("POST") ? Set.of("url", "body") : Set.of("url");
        if (!(decoded instanceof Map<?, ?> values) || !allowed.containsAll(values.keySet())
                || !values.keySet().containsAll(required)) {
            throw new IllegalArgumentException("HTTP 输入字段非法");
        }
        String url = text(values, "url", false, 8192);
        String body = method.equals("POST") ? text(values, "body", true, Integer.MAX_VALUE) : null;
        if (body != null && body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BODY_BYTES) {
            throw new IllegalArgumentException("HTTP 请求 body 超过上限");
        }
        String contentType = values.containsKey("content_type")
                ? text(values, "content_type", false, 256) : null;
        var headers = new LinkedHashMap<String, String>();
        if (values.containsKey("headers")) {
            if (!(values.get("headers") instanceof Map<?, ?> rawHeaders) || rawHeaders.size() > 64) {
                throw new IllegalArgumentException("HTTP headers 非法");
            }
            for (Map.Entry<?, ?> entry : rawHeaders.entrySet()) {
                if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof String value)
                        || name.isBlank() || name.length() > 128 || value.length() > 8192
                        || containsLineBreak(name) || containsLineBreak(value)
                        || !ALLOWED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                        || SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("HTTP header 不允许");
                }
                headers.put(name, value);
            }
        }
        return new RequestInput(url, body, Map.copyOf(headers), contentType);
    }

    private static String text(Map<?, ?> values, String field, boolean allowEmpty, int maxLength) {
        Object raw = values.get(field);
        if (!(raw instanceof String value) || (!allowEmpty && value.isBlank()) || value.length() > maxLength
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("HTTP 字段非法: " + field);
        }
        return value;
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private OryxTool tool(String name, String description, String schema, String method) {
        return new OryxTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getInputSchema() {
                return schema;
            }

            @Override
            public ToolResult execute(String jsonInput) {
                return HttpTools.this.execute(method, jsonInput);
            }
        };
    }

    private static ToolResult failed(String message) {
        return new ToolResult(false, "", message, false);
    }

    private record RequestInput(String url, String body, Map<String, String> headers, String contentType) {
    }
}
