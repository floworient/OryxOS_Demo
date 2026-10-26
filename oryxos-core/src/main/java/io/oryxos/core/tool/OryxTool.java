package io.oryxos.core.tool;

public interface OryxTool {
    String getName();

    String getDescription();

    String getInputSchema();

    ToolResult execute(String jsonInput);
}
