package io.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.json.JsonParser;

public final class McpToolAdapter implements OryxTool {

    private final ToolCallback callback;

    public McpToolAdapter(ToolCallback callback) {
        this.callback = java.util.Objects.requireNonNull(callback);
    }

    public String getName() { return callback.getToolDefinition().name(); }

    public String getDescription() { return callback.getToolDefinition().description(); }

    public String getInputSchema() { return callback.getToolDefinition().inputSchema(); }

    public ToolResult execute(String jsonInput) {
        try {
            if (jsonInput == null || jsonInput.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256 * 1024) {
                return failed();
            }
            Object decoded = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(jsonInput);
            if (!(decoded instanceof Map<?, ?>)) {
                return failed();
            }
            String content = callback.call(jsonInput);
            if (content == null || content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024 * 1024) {
                return failed();
            }
            return new ToolResult(true, content, null, false);
        }
        catch (RuntimeException | java.io.IOException exception) {
            return failed();
        }
    }

    private static ToolResult failed() {
        return new ToolResult(false, "", "MCP 工具执行失败", false);
    }
}
