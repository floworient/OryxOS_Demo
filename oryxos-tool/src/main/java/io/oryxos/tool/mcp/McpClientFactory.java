package io.oryxos.tool.mcp;

@FunctionalInterface
public interface McpClientFactory {
    McpConnection connect(McpServerDefinition definition);
}
