package io.oryxos.tool.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

class AnnotatedToolAdapterTest {

    @Test
    void discoversExplicitAnnotatedMethodsAndPreservesSchemaAndSingleExecution() {
        var fixture = new Fixture();
        var tools = new AnnotatedToolDiscovery().discover(List.of(fixture, new Object()));

        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.getName()).isEqualTo("greet");
            assertThat(tool.getDescription()).isEqualTo("问候指定用户");
            assertThat(tool.getInputSchema()).contains("name", "object", "用户姓名");
            assertThat(tool.execute("{\"name\":\"小王\"}").content()).contains("你好，小王");
        });
        assertThat(fixture.calls).hasValue(1);
        assertThat(tools.getFirst().execute("{\"name\":\"小王\",\"unknown\":true}").success()).isFalse();
        assertThat(tools.getFirst().execute("{}").success()).isFalse();
        assertThat(tools.getFirst().execute("{\"name\":123}").success()).isFalse();
        assertThat(fixture.calls).hasValue(1);
    }

    static final class Fixture {
        private final AtomicInteger calls = new AtomicInteger();

        @Tool(name = "greet", description = "问候指定用户")
        String greet(@ToolParam(description = "用户姓名") String name) {
            calls.incrementAndGet();
            return "你好，" + name;
        }
    }

    @Test
    void acceptsDynamicMapValuesWhenGeneratedSchemaHasNoPropertyRestrictions() {
        var tool = new AnnotatedToolDiscovery().discover(List.of(new MapFixture())).getFirst();
        assertThat(tool.execute("{\"values\":{\"name\":\"小王\"}}").success()).as(tool.getInputSchema()).isTrue();
        assertThat(tool.execute("{\"values\":123}").success()).isFalse();
    }

    static final class MapFixture {
        @Tool(description = "读取映射")
        String mapping(java.util.Map<String, String> values) {
            return values.get("name");
        }
    }
}
