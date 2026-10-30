package io.oryxos.tool.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.oryxos.core.tool.OryxTool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.mcp.SyncMcpToolCallback;

/** 使用官方 MCP Java SDK 创建同步 stdio/SSE 客户端。 */
public final class SpringMcpClientFactory implements McpClientFactory {

    private static final Duration MAX_INITIALIZATION_TIMEOUT = Duration.ofSeconds(30);

    @Override
    public McpConnection connect(McpServerDefinition definition) {
        McpSyncClient client = McpClient.sync(transport(definition))
                .requestTimeout(definition.requestTimeout())
                .initializationTimeout(min(definition.requestTimeout(), MAX_INITIALIZATION_TIMEOUT))
                .build();
        try {
            client.initialize();
            return new SpringMcpConnection(client);
        }
        catch (RuntimeException exception) {
            try {
                client.close();
            }
            catch (RuntimeException ignored) {
                // 初始化异常是主因，关闭异常不得覆盖它。
            }
            if (exception instanceof IllegalArgumentException) {
                throw new IllegalArgumentException("MCP 客户端配置非法");
            }
            throw new McpConnectionException("MCP server 连接失败", exception);
        }
    }

    McpClientTransport transport(McpServerDefinition definition) {
        return switch (definition.transport()) {
            case STDIO -> new StdioClientTransport(
                    ServerParameters.builder(definition.command().toString())
                            .args(definition.arguments())
                            .env(definition.environment())
                            .build(),
                    McpJsonMapper.getDefault());
            case SSE -> HttpClientSseClientTransport.builder(definition.url().toString())
                    .connectTimeout(min(definition.requestTimeout(), MAX_INITIALIZATION_TIMEOUT))
                    .customizeRequest(builder -> definition.headers().forEach(builder::header))
                    .build();
        };
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static final class SpringMcpConnection implements McpConnection {

        private final McpSyncClient client;

        private SpringMcpConnection(McpSyncClient client) {
            this.client = client;
        }

        @Override
        public List<OryxTool> listTools() {
            var adapters = new ArrayList<OryxTool>();
            String cursor = null;
            do {
                var result = cursor == null ? client.listTools() : client.listTools(cursor);
                result.tools().stream()
                        .map(tool -> new SyncMcpToolCallback(client, tool))
                        .map(McpToolAdapter::new)
                        .forEach(adapters::add);
                cursor = result.nextCursor();
            }
            while (cursor != null && !cursor.isBlank());
            return List.copyOf(adapters);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
