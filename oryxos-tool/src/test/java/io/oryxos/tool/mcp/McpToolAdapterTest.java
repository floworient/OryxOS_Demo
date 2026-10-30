package io.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class McpToolAdapterTest {

    @Test
    void forwardsJsonObjectExactlyOnceAndMapsCallbackResult() {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        when(definition.name()).thenReturn("github_report");
        when(definition.description()).thenReturn("GitHub 日报");
        when(definition.inputSchema()).thenReturn("{\"type\":\"object\"}");
        when(callback.call("{\"day\":\"today\"}")).thenReturn("{\"items\":1}");
        var adapter = new McpToolAdapter(callback);

        assertThat(adapter.execute("{\"day\":\"today\"}").content()).contains("items");
        verify(callback).call("{\"day\":\"today\"}");
        assertThat(adapter.execute("[]").success()).isFalse();
    }
}
