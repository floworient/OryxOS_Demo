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
            Object input = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(jsonInput);
            if (!(input instanceof Map<?, ?>)) {
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
}
