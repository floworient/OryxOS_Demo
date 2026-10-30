package io.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpringMcpClientFactoryTest {

    private final SpringMcpClientFactory factory = new SpringMcpClientFactory();

    @Test
    void constructsStdioTransportFromValidatedDefinition() {
        var definition = new McpServerDefinition("local", McpTransport.STDIO, Duration.ofSeconds(5),
                Path.of("java"), List.of("-version"), Map.of("TOKEN", "secret"), null, Map.of());

        assertThat(factory.transport(definition)).isInstanceOf(StdioClientTransport.class);
    }

    @Test
    void constructsSseTransportFromValidatedDefinition() {
        var definition = new McpServerDefinition("remote", McpTransport.SSE, Duration.ofSeconds(5),
                null, List.of(), Map.of(), URI.create("http://127.0.0.1:8080/sse"),
                Map.of("Authorization", "Bearer secret"));

        assertThat(factory.transport(definition)).isInstanceOf(HttpClientSseClientTransport.class);
    }
}
