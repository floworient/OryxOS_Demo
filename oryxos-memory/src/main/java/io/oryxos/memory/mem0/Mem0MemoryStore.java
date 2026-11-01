package io.oryxos.memory.mem0;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryEntryPolicy;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemorySnapshot;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.util.json.JsonParser;

public final class Mem0MemoryStore implements LongTermMemoryStore {
    private final Mem0Client client;

    private final int archivalMaxCharacters;

    private final int recallMaxResults;

    private final int entryMaxCharacters;

    public Mem0MemoryStore(Mem0Client client, int archivalMaxCharacters,
            int recallMaxResults, int entryMaxCharacters) {
        this.client = java.util.Objects.requireNonNull(client);
        this.archivalMaxCharacters = positive(archivalMaxCharacters);
        this.recallMaxResults = positive(recallMaxResults);
        this.entryMaxCharacters = positive(entryMaxCharacters);
    }

    @Override
    public void append(MemoryOwner owner, String content, MemoryScope scope) {
        String normalized = MemoryEntryPolicy.normalizeContent(content, entryMaxCharacters);
        client.post("/memories", JsonParser.toJson(Map.of(
                "messages", List.of(Map.of("role", "user", "content", normalized)),
                "agent_id", owner.key(), "infer", false,
                "metadata", Map.of("scope", scope.name(), "created_by", "oryxos"))));
    }

    @Override
    public MemorySnapshot load(MemoryOwner owner) {
        String ownerQuery = URLEncoder.encode(owner.key(), StandardCharsets.UTF_8);
        List<MemoryEntry> all = ownerEntries(owner, ownerQuery);
        List<MemoryEntry> core = all.stream().filter(item -> item.scope() == MemoryScope.CORE).toList();
        List<MemoryEntry> archival = all.stream().filter(item -> item.scope() == MemoryScope.ARCHIVAL).toList();
        List<MemoryEntry> bounded = bound(archival);
        return new MemorySnapshot(owner, core, bounded, bounded.size() < archival.size(),
                "mem0", capabilities());
    }

    @Override
    public List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "召回关键词不能为空");
        }
        String body = JsonParser.toJson(Map.of("query", keyword.trim(), "agent_id", owner.key()));
        List<MemoryEntry> matches = parse(client.post("/search", body), owner).stream()
                .filter(item -> item.scope() == MemoryScope.ARCHIVAL).toList();
        List<MemoryEntry> ordered = MemoryEntryPolicy.stable(matches);
        return List.copyOf(ordered.subList(Math.max(0, ordered.size() - recallMaxResults), ordered.size()));
    }

    @Override
    public MemoryCapabilities capabilities() {
        return MemoryCapabilities.DELEGATED;
    }

    @Override
    public List<MemoryEntry> archivalEntries(MemoryOwner owner) {
        String ownerQuery = URLEncoder.encode(owner.key(), StandardCharsets.UTF_8);
        return ownerEntries(owner, ownerQuery).stream()
                .filter(item -> item.scope() == MemoryScope.ARCHIVAL).toList();
    }

    private List<MemoryEntry> ownerEntries(MemoryOwner owner, String encodedOwner) {
        return parse(client.get("/memories?agent_id=" + encodedOwner), owner);
    }

    private List<MemoryEntry> parse(String json, MemoryOwner expectedOwner) {
        try {
            var root = JsonParser.getObjectMapper().readTree(json);
            var results = root.path("results");
            if (!root.isObject() || !results.isArray()) {
                throw invalid();
            }
            List<MemoryEntry> entries = new ArrayList<>();
            for (var node : results) {
                String id = text(node, "id");
                String content = text(node, "memory");
                String agentId = text(node, "agent_id");
                String scopeValue = text(node.path("metadata"), "scope");
                String created = text(node, "created_at");
                if (!agentId.equals(expectedOwner.key())) {
                    throw invalid();
                }
                String normalized = MemoryEntryPolicy.normalizeContent(content, entryMaxCharacters);
                entries.add(new MemoryEntry("mem0:" + id, expectedOwner,
                        MemoryScope.valueOf(scopeValue), normalized, Instant.parse(created)));
            }
            return MemoryEntryPolicy.stable(entries);
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException exception) {
            throw invalid();
        }
    }

    private List<MemoryEntry> bound(List<MemoryEntry> entries) {
        return MemoryEntryPolicy.newestWithinPromptLimit(entries, archivalMaxCharacters);
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) {
            throw invalid();
        }
        return node.path(field).asText();
    }

    private static int positive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("记忆上限必须大于 0");
        }
        return value;
    }

    private static MemoryException invalid() {
        return new MemoryException(MemoryException.Code.MALFORMED_STORE, "Mem0 响应协议无效");
    }
}
