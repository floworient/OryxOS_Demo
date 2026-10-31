package io.oryxos.memory.mem0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.memory.MemoryException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Mem0ProtocolValidatorTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void validatesOpenApiWithOneReadOnlyRequestAndSelfHostedPaths() throws Exception {
        var calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi.json", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            respond(exchange, 200, validOpenApi());
        });
        server.start();
        new Mem0ProtocolValidator(client()).validate();
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsHostedV1OrSchemasWithoutMetadata() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi.json", exchange -> respond(exchange, 200,
                "{\"paths\":{\"/v1/memories\":{\"get\":{},\"post\":{}},"
                        + "\"/search\":{\"post\":{}}}}"));
        server.start();
        assertThatThrownBy(() -> new Mem0ProtocolValidator(client()).validate())
                .isInstanceOf(MemoryException.class).hasMessageContaining("协议");
    }

    @Test
    void sendsApiKeyAndRejectsUnauthorizedRedirectAndOversizedResponses() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/unauthorized", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-API-Key")).isEqualTo("test-key");
            respond(exchange, 401, "secret-token");
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/openapi.json");
            respond(exchange, 302, "redirect");
        });
        server.createContext("/large", exchange -> respond(exchange, 200, "x".repeat(64)));
        server.start();

        assertThatThrownBy(() -> client().get("/unauthorized"))
                .isInstanceOf(MemoryException.class).hasMessageNotContaining("secret-token");
        assertThatThrownBy(() -> client().get("/redirect")).isInstanceOf(MemoryException.class);
        var smallClient = new Mem0Client(baseUrl(), "test-key", Duration.ofSeconds(1),
                Duration.ofSeconds(1), 8);
        assertThatThrownBy(() -> smallClient.get("/large")).isInstanceOf(MemoryException.class);
    }

    @Test
    void rejectsInvalidJsonAndTimesOutWithoutRetry() throws Exception {
        var calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi.json", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(200);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "not-json");
        });
        server.start();
        var timeoutClient = new Mem0Client(baseUrl(), "test-key", Duration.ofSeconds(1),
                Duration.ofMillis(30), 1024);

        assertThatThrownBy(() -> new Mem0ProtocolValidator(timeoutClient).validate())
                .isInstanceOf(MemoryException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsInvalidOpenApiJson() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi.json", exchange -> respond(exchange, 200, "not-json"));
        server.start();

        assertThatThrownBy(() -> new Mem0ProtocolValidator(client()).validate())
                .isInstanceOf(MemoryException.class).hasMessageContaining("协议");
    }

    private Mem0Client client() {
        return new Mem0Client(baseUrl(),
                "test-key", Duration.ofSeconds(1), Duration.ofSeconds(1), 1024 * 1024);
    }

    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static String validOpenApi() {
        return "{\"paths\":{\"/memories\":{\"get\":{},\"post\":{}},"
                + "\"/search\":{\"post\":{}}},\"components\":{\"schemas\":{"
                + "\"Memory\":{\"properties\":{\"agent_id\":{},\"metadata\":{}}}}}}";
    }

    static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
