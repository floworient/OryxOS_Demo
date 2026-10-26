package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.type.TypeReference;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import io.oryxos.provider.support.StubChatModel;
import org.springframework.ai.util.json.JsonParser;

class ToolSchemaAdapterTest {

    @Test
    void preservesObjectSchemaAndNeverExecutesTool() {
        var executions = new AtomicInteger();
        var schema = """
                {"type":"object","properties":{"city":{"type":"string","enum":["杭州","上海"]},
                "options":{"type":"object","properties":{"unit":{"type":"string"}}}},"required":["city"]}
                """;
        OryxTool tool = tool(schema, executions);

        var callback = new ToolSchemaAdapter().adapt(tool);

        assertThat(callback.getToolDefinition().name()).isEqualTo("weather");
        assertThat(callback.getToolDefinition().description()).isEqualTo("查询天气");
        var mapType = new TypeReference<Map<String, Object>>() { };
        assertThat(JsonParser.fromJson(callback.getToolDefinition().inputSchema(), mapType))
                .isEqualTo(JsonParser.fromJson(schema, mapType));
        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> callback.call("{}", new ToolContext(Map.of())))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(executions).hasValue(0);
    }

    @Test
    void rejectsInvalidOrNonObjectSchema() {
        var adapter = new ToolSchemaAdapter();
        assertThatThrownBy(() -> adapter.adapt(tool("not-json", new AtomicInteger())))
                .isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> adapter.adapt(tool("[]", new AtomicInteger())))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void providerPreservesSchemaCallbackButDisablesAutomaticExecution() {
        var executions = new AtomicInteger();
        var callback = new ToolSchemaAdapter().adapt(tool(
                "{\"type\":\"object\",\"properties\":{}}", executions));
        var options = DefaultToolCallingChatOptions.builder().toolCallbacks(callback).build();
        var model = StubChatModel.toolCall("m");
        var definition = new ProviderDefinition("alpha", ProviderDefinition.Type.DASHSCOPE,
                "m", null, "key");
        var service = new DefaultProviderService(new ProviderRegistry(List.of(definition),
                Map.of("alpha", model)), record -> record);

        var response = service.chat(null,
                DefaultProviderServiceChatTest.profile("a", "alpha", "m", null),
                new Prompt("x", options));

        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("weather");
            assertThat(call.argumentsJson()).isEqualTo("{\"city\":\"杭州\"}");
        });
        assertThat(model.lastPrompt().getOptions()).isInstanceOfSatisfying(
                DefaultToolCallingChatOptions.class, sent -> {
                    assertThat(sent.getToolCallbacks()).containsExactly(callback);
                    assertThat(sent.getInternalToolExecutionEnabled()).isFalse();
                });
        assertThat(executions).hasValue(0);
    }

    private static OryxTool tool(String schema, AtomicInteger executions) {
        return new OryxTool() {
            @Override
            public String getName() {
                return "weather";
            }

            @Override
            public String getDescription() {
                return "查询天气";
            }

            @Override
            public String getInputSchema() {
                return schema;
            }

            @Override
            public ToolResult execute(String jsonInput) {
                executions.incrementAndGet();
                return new ToolResult(true, "ok", null, false);
            }
        };
    }
}
