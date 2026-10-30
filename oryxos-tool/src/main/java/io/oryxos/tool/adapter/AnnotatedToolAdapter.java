package io.oryxos.tool.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.json.JsonParser;

public final class AnnotatedToolAdapter implements OryxTool {

    private final ToolCallback callback;

    public AnnotatedToolAdapter(ToolCallback callback) {
        this.callback = java.util.Objects.requireNonNull(callback);
    }

    @Override
    public String getName() {
        return callback.getToolDefinition().name();
    }

    @Override
    public String getDescription() {
        return callback.getToolDefinition().description();
    }

    @Override
    public String getInputSchema() {
        return callback.getToolDefinition().inputSchema();
    }

    @Override
    public ToolResult execute(String jsonInput) {
        try {
            if (jsonInput == null || jsonInput.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256 * 1024) {
                return failed();
            }
            Object input = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(jsonInput);
            if (!(input instanceof Map<?, ?> values)) {
                return failed();
            }
            var schema = JsonParser.getObjectMapper().readTree(getInputSchema());
            if (!matches(JsonParser.getObjectMapper().valueToTree(values), schema, schema, 0)) {
                return failed();
            }
            return new ToolResult(true, callback.call(jsonInput), null, false);
        }
        catch (RuntimeException | java.io.IOException exception) {
            return failed();
        }
    }

    private static ToolResult failed() {
        return new ToolResult(false, "", "Java 工具执行失败", false);
    }

    private static boolean matches(com.fasterxml.jackson.databind.JsonNode value,
            com.fasterxml.jackson.databind.JsonNode schema, com.fasterxml.jackson.databind.JsonNode root, int depth) {
        if (depth > 32) {
            return false;
        }
        if (schema.has("$ref")) {
            String ref = schema.path("$ref").asText();
            return ref.startsWith("#/") && matches(value, root.at(ref.substring(1)), root, depth + 1);
        }
        if (schema.isMissingNode()) {
            return false;
        }
        String type = schema.path("type").asText();
        if (type.equals("object")) {
            if (!value.isObject()) {
                return false;
            }
            for (var required : schema.path("required")) {
                if (!value.has(required.asText())) {
                    return false;
                }
            }
            var fields = value.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                var property = schema.path("properties").get(field.getKey());
                if (property == null) {
                    var additional = schema.get("additionalProperties");
                    if (additional != null && additional.isObject()) {
                        property = additional;
                    }
                    else if (additional == null || (additional.isBoolean() && additional.asBoolean())) {
                        continue;
                    }
                    else {
                        return false;
                    }
                }
                if (!matches(field.getValue(), property, root, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (type.equals("array")) {
            if (!value.isArray()) {
                return false;
            }
            for (var item : value) {
                if (!matches(item, schema.path("items"), root, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }
}
