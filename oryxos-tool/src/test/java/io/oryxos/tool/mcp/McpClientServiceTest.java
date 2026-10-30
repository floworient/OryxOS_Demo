package io.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.core.tool.ToolSource;
import io.oryxos.tool.registry.DefaultToolRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class McpClientServiceTest {

    @Test
    void localConfigurationErrorFailsInsteadOfSkippingServer() {
        var service = new McpClientService(definition -> {
            throw new IllegalArgumentException("canary-secret");
        }, new DefaultToolRegistry());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.initialize(List.of(definition("bad"))))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("canary-secret");
    }

    @Test
    void registersHealthyServerSkipsConnectionFailureAndClosesConnections() {
        var registry = new DefaultToolRegistry();
        var closed = new AtomicBoolean();
        McpClientFactory factory = definition -> {
            if (definition.name().equals("down")) {
                throw new McpConnectionException("canary-secret");
            }
            return new McpConnection() {
                @Override
                public List<OryxTool> listTools() {
                    return List.of(tool("github_report"));
                }

                @Override
                public void close() {
                    closed.set(true);
                }
            };
        };
        var service = new McpClientService(factory, registry);

        service.initialize(List.of(definition("github"), definition("down")));
        assertThat(registry.registrations()).singleElement().satisfies(registration -> {
            assertThat(registration.source()).isEqualTo(ToolSource.MCP);
            assertThat(registration.sourceName()).isEqualTo("github");
        });
        service.close();
        assertThat(closed).isTrue();
    }

    private static McpServerDefinition definition(String name) {
        return new McpServerDefinition(name, McpTransport.SSE, Duration.ofSeconds(30), null,
                List.of(), Map.of(), java.net.URI.create("https://example.com/sse"), Map.of());
    }

    private static OryxTool tool(String name) {
        return new OryxTool() {
            public String getName() {
                return name;
            }

            public String getDescription() {
                return "说明";
            }

            public String getInputSchema() {
                return "{\"type\":\"object\"}";
            }

            public ToolResult execute(String input) {
                return new ToolResult(true, "ok", null, false);
            }
        };
    }
}
