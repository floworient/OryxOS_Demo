package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

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
}
