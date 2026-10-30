package io.oryxos.tool.mcp;

import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolSource;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McpClientService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpClientService.class);

    private final McpClientFactory factory;

    private final ToolRegistry registry;

    private final List<McpConnection> connections = new ArrayList<>();

    public McpClientService(McpClientFactory factory, ToolRegistry registry) {
        this.factory = java.util.Objects.requireNonNull(factory);
        this.registry = java.util.Objects.requireNonNull(registry);
    }

    public void initialize(List<McpServerDefinition> definitions) {
        for (McpServerDefinition definition : definitions) {
            McpConnection connection = null;
            List<io.oryxos.core.tool.OryxTool> tools;
            try {
                connection = factory.connect(definition);
                tools = List.copyOf(connection.listTools());
            }
            catch (RuntimeException exception) {
                if (connection != null) {
                    closeQuietly(connection);
                }
                if (exception instanceof IllegalArgumentException) {
                    throw new IllegalStateException("MCP 客户端配置非法");
                }
                LOGGER.warn("MCP server 不可用，已跳过 serverName={}", definition.name());
                continue;
            }
            try {
                for (var tool : tools) {
                    registry.register(new ToolRegistration(tool, ToolSource.MCP, definition.name()));
                }
                connections.add(connection);
            }
            catch (RuntimeException exception) {
                closeQuietly(connection);
                throw exception;
            }
        }
    }

    @Override
    public void close() {
        var reversed = new ArrayList<>(connections);
        java.util.Collections.reverse(reversed);
        reversed.forEach(McpClientService::closeQuietly);
        connections.clear();
    }

    private static void closeQuietly(McpConnection connection) {
        try {
            connection.close();
        }
        catch (RuntimeException exception) {
            LOGGER.warn("MCP connection 关闭失败");
        }
    }
}
