package io.oryxos.web.session;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** 会话消息的安全公共投影。 */
public record MessageView(String role, String text, List<ToolReferenceView> toolCalls,
        List<ToolReferenceView> toolResults) {

    public MessageView {
        toolCalls = List.copyOf(toolCalls);
        toolResults = List.copyOf(toolResults);
    }

    static MessageView from(Message message) {
        if (message instanceof UserMessage) {
            return new MessageView("user", message.getText(), List.of(), List.of());
        }
        if (message instanceof AssistantMessage assistant) {
            return new MessageView("assistant", assistant.getText(),
                    assistant.getToolCalls().stream()
                            .map(call -> new ToolReferenceView(call.name(), call.id())).toList(),
                    List.of());
        }
        if (message instanceof ToolResponseMessage tool) {
            return new MessageView("tool", null, List.of(),
                    tool.getResponses().stream()
                            .map(result -> new ToolReferenceView(result.name(), result.id())).toList());
        }
        throw new IllegalArgumentException("不支持的会话消息角色");
    }

    public record ToolReferenceView(String name, String id) { }
}
