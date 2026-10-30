package io.oryxos.tool.mcp;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record McpServerDefinition(String name, McpTransport transport, Duration requestTimeout,
                                  Path command, List<String> arguments, Map<String, String> environment,
                                  URI url, Map<String, String> headers) {
    public McpServerDefinition {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("MCP server name 非法");
        }
        java.util.Objects.requireNonNull(transport, "transport");
        java.util.Objects.requireNonNull(requestTimeout, "requestTimeout");
        arguments = List.copyOf(arguments);
        environment = Map.copyOf(environment);
        headers = Map.copyOf(headers);
        if (transport == McpTransport.STDIO && (command == null || url != null)) {
            throw new IllegalArgumentException("stdio MCP 定义非法");
        }
        if (transport == McpTransport.SSE && (url == null || command != null)) {
            throw new IllegalArgumentException("sse MCP 定义非法");
        }
    }

    @Override
    public String toString() {
        return "McpServerDefinition[name=" + name + ", transport=" + transport
                + ", requestTimeout=" + requestTimeout + "]";
    }
}
