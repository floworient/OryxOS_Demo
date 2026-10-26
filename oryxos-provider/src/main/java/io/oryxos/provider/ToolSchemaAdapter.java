package io.oryxos.provider;

import java.util.Map;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.core.tool.OryxTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.JsonParser;

public final class ToolSchemaAdapter {

    public ToolCallback adapt(OryxTool tool) {
        if (tool == null || isBlank(tool.getName()) || isBlank(tool.getDescription())
                || isBlank(tool.getInputSchema())) {
            throw invalid("工具名称、说明和输入 Schema 均不能为空", null);
        }
        try {
            var parsed = JsonParser.fromJson(tool.getInputSchema(), Object.class);
            if (!(parsed instanceof Map<?, ?> map) || !"object".equals(map.get("type"))) {
                throw invalid("工具输入 Schema 必须是 JSON object schema", null);
            }
        }
        catch (ProviderException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw invalid("工具输入 Schema 不是合法 JSON", exception);
        }
        var definition = new DefaultToolDefinition(
                tool.getName(), tool.getDescription(), tool.getInputSchema());
        return new SchemaOnlyToolCallback(definition);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ProviderException invalid(String message, Throwable cause) {
        return new ProviderException(ProviderErrorCode.INVALID_TOOL_SCHEMA, null, null, message, cause);
    }

    private record SchemaOnlyToolCallback(ToolDefinition toolDefinition) implements ToolCallback {
        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            throw new UnsupportedOperationException("Schema-only callback 不允许执行工具");
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            throw new UnsupportedOperationException("Schema-only callback 不允许执行工具");
        }
    }
}
