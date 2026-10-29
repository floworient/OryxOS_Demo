package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.tool.sandbox.WhitelistSandbox;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpToolsTest {

    private HttpServer server;

    private AtomicInteger requests;

    private AtomicReference<String> posted;

    private HttpTools tools;

    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        requests = new AtomicInteger();
        posted = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/get", exchange -> {
            requests.incrementAndGet();
            byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/post", exchange -> {
            requests.incrementAndGet();
            posted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    + "|" + exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "/get");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            requests.incrementAndGet();
            try {
                Thread.sleep(2_000);
                exchange.sendResponseHeaders(204, -1);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        tools = new HttpTools(new WhitelistSandbox(Set.of("127.0.0.1")), client,
                Duration.ofMillis(300), 1024 * 1024);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void getAndPostSendExactlyOneRequestAndReturnStatusAndBody() {
        assertThat(tool("http_get").execute("{\"url\":\"" + baseUrl + "/get\"}").content())
                .contains("\"status\":200", "hello");
        assertThat(tool("http_post").execute("{\"url\":\"" + baseUrl
                + "/post\",\"body\":\"payload\",\"content_type\":\"text/plain\"}").content())
                .contains("\"status\":204");
        assertThat(posted).hasValue("payload|text/plain");
        assertThat(requests).hasValue(2);
    }

    @Test
    void neverFollowsRedirectAndNeverRetries() {
        var result = tool("http_get").execute("{\"url\":\"" + baseUrl + "/redirect\"}");
        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("\"status\":302");
        assertThat(requests).hasValue(1);
    }

    @Test
    void rejectsSensitiveHeadersAndDisallowedHostsBeforeNetwork() {
        var sensitive = tool("http_get").execute("{\"url\":\"" + baseUrl
                + "/get\",\"headers\":{\"Authorization\":\"secret\"}}");
        assertThat(sensitive.success()).isFalse();
        var disallowed = tool("http_get").execute("{\"url\":\"http://localhost:"
                + server.getAddress().getPort() + "/get\"}");
        assertThat(disallowed.success()).isFalse();
        assertThat(requests).hasValue(0);
        assertThat(sensitive.errorMessage()).doesNotContain("secret");
    }

    @Test
    void timeoutUnknownFieldsAndTrailingJsonFailWithoutRetry() {
        assertThat(tool("http_get").execute("{\"url\":\"" + baseUrl + "/slow\"}").success()).isFalse();
        assertThat(requests).hasValue(1);
        assertThat(tool("http_get").execute("{\"url\":\"" + baseUrl
                + "/get\",\"extra\":1}").success()).isFalse();
        assertThat(tool("http_post").execute("{} {}").success()).isFalse();
        assertThat(requests).hasValue(1);
    }

    private OryxTool tool(String name) {
        return tools.all().stream().filter(tool -> tool.getName().equals(name)).findFirst().orElseThrow();
    }
}
