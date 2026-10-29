package io.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class WebhookNotifyAdapterTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsExactLesson19PayloadOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).startsWith("application/json");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";

        new WebhookNotifyAdapter(RestClient.create()).send(
                new NotifyTarget("webhook", Map.of("url", url)), "hello");

        assertThat(calls).hasValue(1);
        assertThat(body).hasValue("{\"content\":\"hello\"}");
    }

    @Test
    void turnsServerFailureIntoSafeNonRetryingException() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] response = "private-response".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";

        assertThatThrownBy(() -> new WebhookNotifyAdapter(RestClient.create()).send(
                new NotifyTarget("webhook", Map.of("url", url)), "hello"))
                .isInstanceOf(NotifyDeliveryException.class)
                .hasMessageNotContaining(url).hasMessageNotContaining("private-response");
    }

    @Test
    void rejectsRedirectInsteadOfFollowingIt() throws Exception {
        AtomicInteger targetCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            targetCalls.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect";

        assertThatThrownBy(() -> new WebhookNotifyAdapter(RestClient.create()).send(
                new NotifyTarget("webhook", Map.of("url", url)), "hello"))
                .isInstanceOf(NotifyDeliveryException.class);
        assertThat(targetCalls).hasValue(0);
    }
}
