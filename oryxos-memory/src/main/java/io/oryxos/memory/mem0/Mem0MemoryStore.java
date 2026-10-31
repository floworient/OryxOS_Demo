package io.oryxos.memory.mem0;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
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
        if (content == null || content.isBlank() || content.trim().length() > entryMaxCharacters) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "记忆内容为空或超过长度上限");
        }
        client.post("/memories", JsonParser.toJson(Map.of(
                "messages", List.of(Map.of("role", "user", "content", content.trim())),
                "agent_id", owner.key(), "infer", false,
                "metadata", Map.of("scope", scope.name(), "created_by", "oryxos"))));
    }

    @Override
    public MemorySnapshot load(MemoryOwner owner) {
        String ownerQuery = URLEncoder.encode(owner.key(), StandardCharsets.UTF_8);
        List<MemoryEntry> all = parse(client.get("/memories?agent_id=" + ownerQuery), owner);
        List<MemoryEntry> core = all.stream().filter(item -> item.scope() == MemoryScope.CORE).toList();
        List<MemoryEntry> archival = all.stream().filter(item -> item.scope() == MemoryScope.ARCHIVAL).toList();
        List<MemoryEntry> bounded = bound(archival);
        return new MemorySnapshot(owner, core, bounded, bounded.size() < archival.size(),
                "mem0", capabilities());
    }

    @Override
    public List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword) {
        String body = JsonParser.toJson(Map.of("query", keyword, "agent_id", owner.key()));
        List<MemoryEntry> matches = parse(client.post("/search", body), owner).stream()
                .filter(item -> item.scope() == MemoryScope.ARCHIVAL).toList();
        return List.copyOf(matches.subList(Math.max(0, matches.size() - recallMaxResults), matches.size()));
    }

    @Override
    public MemoryCapabilities capabilities() {
        return MemoryCapabilities.DELEGATED;
    }

    @Override
    public List<MemoryEntry> archivalEntries(MemoryOwner owner) {
        return List.of();
    }

    private List<MemoryEntry> parse(String json, MemoryOwner expectedOwner) {
        try {
            var root = JsonParser.getObjectMapper().readTree(json);
            if (!root.isArray()) {
                throw invalid();
            }
            List<MemoryEntry> entries = new ArrayList<>();
            for (var node : root) {
                String id = text(node, "id");
                String content = text(node, "memory");
                String agentId = text(node, "agent_id");
                String scopeValue = text(node.path("metadata"), "scope");
                String created = text(node, "created_at");
                if (!agentId.equals(expectedOwner.key())) {
                    throw invalid();
                }
                entries.add(new MemoryEntry("mem0:" + id, expectedOwner,
                        MemoryScope.valueOf(scopeValue), content, Instant.parse(created)));
            }
            return List.copyOf(entries);
        }
        catch (MemoryException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw invalid();
        }
    }

    private List<MemoryEntry> bound(List<MemoryEntry> entries) {
        List<MemoryEntry> result = new ArrayList<>();
        int used = 0;
        for (int index = entries.size() - 1; index >= 0; index--) {
            int size = entries.get(index).content().length();
            if (used + size > archivalMaxCharacters) {
                break;
            }
            result.add(0, entries.get(index));
            used += size;
        }
        return List.copyOf(result);
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
