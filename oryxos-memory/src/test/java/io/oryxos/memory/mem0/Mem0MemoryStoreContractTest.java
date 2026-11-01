package io.oryxos.memory.mem0;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.LongTermMemoryStoreContract;
import io.oryxos.core.memory.MemoryOwner;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.util.json.JsonParser;

class Mem0MemoryStoreContractTest extends LongTermMemoryStoreContract {

    private final List<Record> records = new CopyOnWriteArrayList<>();

    private final AtomicInteger sequence = new AtomicInteger();

    private HttpServer server;

    private Mem0MemoryStore memory;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/memories", this::memories);
        server.createContext("/search", this::search);
        server.start();
        var client = new Mem0Client(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "test-key", Duration.ofSeconds(1), Duration.ofSeconds(1), 1024 * 1024);
        memory = new Mem0MemoryStore(client, 80, 2, 512);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Override
    protected LongTermMemoryStore store() {
        return memory;
    }

    @Override
    protected MemoryOwner firstOwner() {
        return MemoryOwner.agent("first");
    }

    @Override
    protected MemoryOwner secondOwner() {
        return MemoryOwner.agent("second");
    }

    private void memories(HttpExchange exchange) throws java.io.IOException {
        if (exchange.getRequestMethod().equals("POST")) {
            JsonNode request = JsonParser.getObjectMapper().readTree(exchange.getRequestBody());
            int id = sequence.incrementAndGet();
            records.add(new Record(Integer.toString(id), request.path("agent_id").asText(),
                    request.path("metadata").path("scope").asText(),
                    request.path("messages").path(0).path("content").asText(),
                    Instant.EPOCH.plusSeconds(id)));
            respond(exchange, List.of(records.get(records.size() - 1)));
            return;
        }
        String owner = queryValue(exchange.getRequestURI().getRawQuery(), "agent_id");
        List<Record> matching = records.stream().filter(item -> item.owner().equals(owner)).toList();
        List<Record> reversed = new ArrayList<>(matching);
        java.util.Collections.reverse(reversed);
        respond(exchange, reversed);
    }

    private void search(HttpExchange exchange) throws java.io.IOException {
        JsonNode request = JsonParser.getObjectMapper().readTree(exchange.getRequestBody());
        String owner = request.path("agent_id").asText();
        String query = request.path("query").asText().toLowerCase(java.util.Locale.ROOT);
        List<Record> matching = records.stream()
                .filter(item -> item.owner().equals(owner))
                .filter(item -> item.content().toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(java.util.Comparator.comparing(Record::createdAt).reversed()).toList();
        respond(exchange, matching);
    }

    private static void respond(HttpExchange exchange, List<Record> records) throws java.io.IOException {
        String body = JsonParser.toJson(java.util.Map.of("results", records.stream()
                .map(Record::asResponse).toList()));
        Mem0ProtocolValidatorTest.respond(exchange, 200, body);
    }

    private static String queryValue(String query, String name) {
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private record Record(String id, String owner, String scope, String content, Instant createdAt) {
        java.util.Map<String, Object> asResponse() {
            return java.util.Map.of("id", id, "agent_id", owner, "memory", content,
                    "metadata", java.util.Map.of("scope", scope), "created_at", createdAt.toString());
        }
    }
}
