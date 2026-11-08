package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;

import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;

class AutomaticToolExecutionDisabledTest {

    @Test
    void schemaCallbackAlwaysRejectsExecution() {
        var executions = new AtomicInteger();
        OryxTool tool = new OryxTool() {
            @Override
            public String getName() {
                return "noop";
            }

            @Override
            public String getDescription() {
                return "不执行";
            }

            @Override
            public String getInputSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public ToolResult execute(String jsonInput) {
                executions.incrementAndGet();
                return new ToolResult(true, "", null, false);
            }
        };
        var callback = new ToolSchemaAdapter().adapt(tool);

        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(executions).hasValue(0);
    }

    @Test
    void mockProviderRequestAlsoDisablesInternalToolExecution() {
        var model = StubChatModel.text("完成", "mock-memory");
        var definition = new ProviderDefinition("mock", ProviderDefinition.Type.MOCK,
                "mock-memory", null, null);
        var service = new DefaultProviderService(
                new ProviderRegistry(List.of(definition), Map.of("mock", model)), record -> record);

        service.chat("session", DefaultProviderServiceChatTest.profile(
                "memory", "mock", "mock-memory", null), new Prompt("记住：事实"));

        assertThat(model.lastPrompt().getOptions()).isInstanceOfSatisfying(
                DefaultToolCallingChatOptions.class,
                options -> assertThat(options.getInternalToolExecutionEnabled()).isFalse());
    }
}
