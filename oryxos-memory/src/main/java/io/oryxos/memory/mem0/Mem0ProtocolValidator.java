package io.oryxos.memory.mem0;

import io.oryxos.core.memory.MemoryException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.ai.util.json.JsonParser;

public final class Mem0ProtocolValidator {
    private final Mem0Client client;

    public Mem0ProtocolValidator(Mem0Client client) {
        this.client = java.util.Objects.requireNonNull(client);
    }

    public void validate() {
        try {
            var root = JsonParser.getObjectMapper().readTree(client.get("/openapi.json"));
            var paths = root.path("paths");
            JsonNode read = paths.path("/memories").path("get");
            JsonNode create = paths.path("/memories").path("post");
            JsonNode search = paths.path("/search").path("post");
            boolean operations = read.isObject() && create.isObject() && search.isObject()
                    && !paths.has("/v1/memories");
            boolean createRequest = requestSupports(root, create, "agent_id", "metadata", "infer");
            boolean ownerRead = hasQueryParameter(root, read, "agent_id")
                    && responseSupports(root, read, "results", "agent_id", "metadata", "memory");
            boolean delegatedSearch = requestSupports(root, search, "query", "agent_id")
                    && responseSupports(root, search, "results", "agent_id", "metadata", "memory");
            if (!operations || !createRequest || !ownerRead || !delegatedSearch) {
                throw invalid();
            }
        }
        catch (MemoryException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw invalid();
        }
    }

    private static boolean requestSupports(JsonNode root, JsonNode operation, String... properties) {
        JsonNode schema = operation.path("requestBody").path("content")
                .path("application/json").path("schema");
        return supports(root, schema, properties);
    }

    private static boolean responseSupports(JsonNode root, JsonNode operation, String... properties) {
        JsonNode responses = operation.path("responses");
        JsonNode response = responses.has("200") ? responses.path("200") : responses.path("201");
        JsonNode schema = response.path("content").path("application/json").path("schema");
        return !declaresShape(schema) || supports(root, schema, properties);
    }

    private static boolean declaresShape(JsonNode schema) {
        return schema.has("$ref") || schema.has("properties") || schema.has("items")
                || schema.has("allOf") || schema.has("anyOf") || schema.has("oneOf");
    }

    private static boolean supports(JsonNode root, JsonNode schema, String... properties) {
        for (String property : properties) {
            if (!containsProperty(root, schema, property, new HashSet<>())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasQueryParameter(JsonNode root, JsonNode operation, String name) {
        for (JsonNode raw : operation.path("parameters")) {
            JsonNode parameter = dereference(root, raw, new HashSet<>());
            if (name.equals(parameter.path("name").asText())
                    && "query".equals(parameter.path("in").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsProperty(JsonNode root, JsonNode raw,
            String property, Set<String> visited) {
        if (raw == null || raw.isMissingNode() || raw.isNull()) {
            return false;
        }
        JsonNode schema = dereference(root, raw, visited);
        if (schema.path("properties").has(property)) {
            return true;
        }
        if (containsProperty(root, schema.path("items"), property, visited)) {
            return true;
        }
        for (String composition : new String[]{"allOf", "anyOf", "oneOf"}) {
            for (JsonNode child : schema.path(composition)) {
                if (containsProperty(root, child, property, visited)) {
                    return true;
                }
            }
        }
        for (JsonNode child : schema.path("properties")) {
            if (containsProperty(root, child, property, visited)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode dereference(JsonNode root, JsonNode node, Set<String> visited) {
        String reference = node.path("$ref").asText();
        if (reference.isEmpty()) {
            return node;
        }
        if (!reference.startsWith("#/components/schemas/") || !visited.add(reference)) {
            return node;
        }
        return root.path("components").path("schemas")
                .path(reference.substring("#/components/schemas/".length()));
    }

    private static MemoryException invalid() {
        return new MemoryException(MemoryException.Code.UNSUPPORTED_CAPABILITY, "Mem0 协议不支持分区记忆");
    }
}
