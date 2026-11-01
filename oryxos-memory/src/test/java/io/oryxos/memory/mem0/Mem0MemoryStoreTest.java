package io.oryxos.memory.mem0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Mem0MemoryStoreTest {
    private HttpServer server;

    @Test
    void inferenceIsOptInForArchivalAndAlwaysDisabledForCore() throws Exception {
        var requests = new java.util.ArrayList<String>();
        server = newServer();
        server.createContext("/memories", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Mem0ProtocolValidatorTest.respond(exchange, 200, "{\"results\":[{\"id\":\"new\"}]}");
        });
        server.start();
        var client = new Mem0Client(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "key", Duration.ofSeconds(1), Duration.ofSeconds(1), 1024);
        var inferred = new Mem0MemoryStore(client, 4000, 20, 4000, true);
        inferred.append(MemoryOwner.agent("alpha"), "fact", MemoryScope.ARCHIVAL);
        inferred.append(MemoryOwner.agent("alpha"), "core", MemoryScope.CORE);
        store().append(MemoryOwner.agent("alpha"), "default", MemoryScope.ARCHIVAL);
        assertThat(requests.get(0)).contains("\"infer\":true");
        assertThat(requests.get(1)).contains("\"infer\":false");
        assertThat(requests.get(2)).contains("\"infer\":false");
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void writesInferFalseOwnerAndScopeThenLoadsPartitionedEntries() throws Exception {
        var request = new AtomicReference<String>();
        server = newServer();
        server.createContext("/memories", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                Mem0ProtocolValidatorTest.respond(exchange, 200,
                        "{\"results\":[{\"id\":\"created-1\"}]}");
            }
            else {
                assertThat(exchange.getRequestURI().getQuery()).isEqualTo("agent_id=alpha");
                Mem0ProtocolValidatorTest.respond(exchange, 200, entry("1", "core fact", "CORE"));
            }
        });
        server.start();
        var store = store();
        store.append(MemoryOwner.agent("alpha"), "private fact", MemoryScope.CORE);
        var snapshot = store.load(MemoryOwner.agent("alpha"));
        assertThat(request.get()).contains("private fact", "\"infer\":false",
                "\"agent_id\":\"alpha\"", "\"scope\":\"CORE\"");
        assertThat(snapshot.core()).extracting(item -> item.content()).containsExactly("core fact");
        assertThat(store.capabilities()).isEqualTo(MemoryCapabilities.DELEGATED);
        assertThat(store.archivalEntries(MemoryOwner.agent("alpha"))).isEmpty();
    }

    @Test
    void searchKeepsOnlyArchivalAndWriteFailureIsNotRetried() throws Exception {
        var writes = new AtomicInteger();
        server = newServer();
        server.createContext("/memories", exchange -> {
            writes.incrementAndGet();
            Mem0ProtocolValidatorTest.respond(exchange, 500, "secret backend failure");
        });
        server.createContext("/search", exchange -> Mem0ProtocolValidatorTest.respond(exchange, 200,
                envelope(single("2", "match", "ARCHIVAL") + ","
                        + single("3", "core", "CORE"))));
        server.start();
        var store = store();
        assertThat(store.recallByKeyword(MemoryOwner.agent("alpha"), "match"))
                .extracting(item -> item.content()).containsExactly("match");
        assertThatThrownBy(() -> store.append(MemoryOwner.agent("alpha"), "value", MemoryScope.ARCHIVAL))
                .isInstanceOf(MemoryException.class).hasMessageNotContaining("secret");
        assertThat(writes).hasValue(1);
    }

    @Test
    void rejectsRecordsMissingPartitionMetadata() throws Exception {
        server = newServer();
        server.createContext("/memories", exchange -> Mem0ProtocolValidatorTest.respond(exchange, 200,
                "{\"results\":[{\"id\":\"1\",\"memory\":\"bad\",\"agent_id\":\"alpha\","
                        + "\"created_at\":\"2026-10-31T00:00:00Z\"}]}"));
        server.start();
        assertThatThrownBy(() -> store().load(MemoryOwner.agent("alpha")))
                .isInstanceOf(MemoryException.class).hasMessageContaining("协议");
    }

    @Test
    void rejectsAmbiguousSuccessfulCreateResponse() throws Exception {
        server = newServer();
        server.createContext("/memories", exchange ->
                Mem0ProtocolValidatorTest.respond(exchange, 200, "{\"results\":[]}"));
        server.start();

        assertThatThrownBy(() -> store().append(MemoryOwner.agent("alpha"),
                "value", MemoryScope.ARCHIVAL))
                .isInstanceOf(MemoryException.class).hasMessageContaining("协议");
    }

    private HttpServer newServer() throws java.io.IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private Mem0MemoryStore store() {
        var client = new Mem0Client(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "test-key", Duration.ofSeconds(1), Duration.ofSeconds(1), 1024 * 1024);
        return new Mem0MemoryStore(client, 4000, 20, 4000);
    }

    private static String entry(String id, String memory, String scope) {
        return envelope(single(id, memory, scope));
    }

    private static String envelope(String entries) {
        return "{\"results\":[" + entries + "]}";
    }

    private static String single(String id, String memory, String scope) {
        return "{\"id\":\"" + id + "\",\"memory\":\"" + memory + "\","
                + "\"agent_id\":\"alpha\",\"metadata\":{\"scope\":\"" + scope + "\"},"
                + "\"created_at\":\"2026-10-31T00:00:00Z\"}";
    }
}
