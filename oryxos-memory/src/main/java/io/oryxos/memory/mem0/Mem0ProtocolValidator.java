package io.oryxos.memory.mem0;

import io.oryxos.core.memory.MemoryException;
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
            boolean operations = paths.path("/memories").has("get")
                    && paths.path("/memories").has("post") && paths.path("/search").has("post")
                    && !paths.has("/v1/memories");
            String schemas = root.path("components").path("schemas").toString();
            if (!operations || !schemas.contains("agent_id") || !schemas.contains("metadata")) {
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

    private static MemoryException invalid() {
        return new MemoryException(MemoryException.Code.UNSUPPORTED_CAPABILITY, "Mem0 协议不支持分区记忆");
    }
}
