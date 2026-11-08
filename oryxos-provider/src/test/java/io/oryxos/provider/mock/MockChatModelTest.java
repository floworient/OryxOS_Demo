package io.oryxos.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class MockChatModelTest {

    private final MockChatModel model = new MockChatModel("mock-memory");

    @Test
    void requestsExactlyOneArchivalMemoryWriteThenReturnsFinalAnswer() {
        var first = model.call(prompt(List.of(new UserMessage("记住：我喜欢喝美式咖啡"))));

        assertThat(first.getMetadata().getUsage().getTotalTokens()).isPositive();
        assertThat(first.getResult().getOutput().getToolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("save_memory");
            assertThat(call.arguments()).contains("我喜欢喝美式咖啡").contains("archival");
        });
        var call = first.getResult().getOutput().getToolCalls().getFirst();
        var assistant = AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        var toolResult = ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse(call.id(), call.name(),
                        "{\"success\":true,\"output\":\"已保存\",\"error\":null,\"truncated\":false}")))
                .build();

        var second = model.call(prompt(List.of(
                new UserMessage("记住：我喜欢喝美式咖啡"), assistant, toolResult)));

        assertThat(second.getResult().getOutput().getText()).isNotBlank();
        assertThat(second.getResult().getOutput().getToolCalls()).isEmpty();
        assertThat(second.getMetadata().getUsage().getTotalTokens()).isPositive();
    }

    @Test
    void rejectsUnsupportedInputInvisibleToolAndMismatchedToolResult() {
        assertThatThrownBy(() -> model.call(prompt(List.of(new UserMessage("你好")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("记住：");

        assertThatThrownBy(() -> model.call(new Prompt(List.of(new UserMessage("记住：事实")))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("save_memory");

        var assistant = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-x", "function", "save_memory",
                        "{\"content\":\"事实\",\"scope\":\"archival\"}"))).build();
        var wrong = ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("other", "save_memory", "{\"success\":true}"))).build();
        assertThatThrownBy(() -> model.call(prompt(List.of(
                new UserMessage("记住：事实"), assistant, wrong))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("工具结果");
    }

    @Test
    void derivesEachConversationPhaseOnlyFromItsPrompt() {
        var coffee = model.call(prompt(List.of(new UserMessage("记住：咖啡"))));
        var tea = model.call(prompt(List.of(new UserMessage("记住：茶"))));

        assertThat(coffee.getResult().getOutput().getToolCalls().getFirst().arguments()).contains("咖啡");
        assertThat(tea.getResult().getOutput().getToolCalls().getFirst().arguments()).contains("茶");
    }

    private static Prompt prompt(List<org.springframework.ai.chat.messages.Message> messages) {
        return new Prompt(messages, ToolCallingChatOptions.builder()
                .toolNames("save_memory")
                .internalToolExecutionEnabled(false)
                .build());
    }
}
