package io.oryxos.core.context;

import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** 用 UTF-8 字节和固定协议余量估算输入规模。 */
public final class PromptInputEstimator {

    private PromptInputEstimator() {
    }

    public static long estimate(Prompt prompt) {
        long units = 128 + prompt.getInstructions().stream().mapToLong(PromptInputEstimator::messageUnits).sum();
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            for (var callback : options.getToolCallbacks()) {
                var definition = callback.getToolDefinition();
                units += 32L + bytes(definition.name()) + bytes(definition.description())
                        + bytes(definition.inputSchema());
            }
        }
        return units;
    }

    static long messageUnits(Message message) {
        long units = 32L + bytes(message.getMessageType().getValue()) + bytes(message.getText());
        if (message instanceof AssistantMessage assistant) {
            for (var call : assistant.getToolCalls()) {
                units += 32L + bytes(call.id()) + bytes(call.type()) + bytes(call.name()) + bytes(call.arguments());
            }
        }
        if (message instanceof ToolResponseMessage tool) {
            for (var response : tool.getResponses()) {
                units += 32L + bytes(response.id()) + bytes(response.name()) + bytes(response.responseData());
            }
        }
        return units;
    }

    static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
