package io.oryxos.provider.mock;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.util.json.JsonParser;

/**
 * 只用于运行时链路验证的确定性模型，不模拟真实模型质量或协议行为。
 */
public final class MockChatModel implements ChatModel {

    private static final String PREFIX = "记住：";

    private static final String TOOL_NAME = "save_memory";

    private final String model;

    public MockChatModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Mock 模型名称不能为空");
        }
        this.model = model;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var messages = prompt.getInstructions();
        var user = messages.stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalArgumentException("Mock Provider 缺少用户输入"));
        var text = io.oryxos.core.context.PromptBuilder.userText(user);
        if (text == null || !text.startsWith(PREFIX) || text.substring(PREFIX.length()).isBlank()) {
            throw new IllegalArgumentException("Mock Provider 只接受“记住：<非空事实>”输入");
        }
        String fact = text.substring(PREFIX.length()).trim();
        var toolResult = messages.stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).reduce((first, second) -> second);
        if (toolResult.isEmpty()) {
            requireVisibleTool(prompt);
            return response(firstRound(fact), 9, 6);
        }
        validateToolResult(messages, toolResult.orElseThrow(), fact);
        return response(new AssistantMessage("已记住这条信息。"), 11, 7);
    }

    private static AssistantMessage firstRound(String fact) {
        String callId = "mock-" + UUID.nameUUIDFromBytes(fact.getBytes(StandardCharsets.UTF_8));
        String arguments = JsonParser.toJson(java.util.Map.of(
                "content", fact,
                "scope", "archival"));
        return AssistantMessage.builder()
                .content("正在保存记忆。")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        callId, "function", TOOL_NAME, arguments)))
                .build();
    }

    private static void requireVisibleTool(Prompt prompt) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions options)) {
            throw new IllegalStateException("Mock Provider 要求可见工具 save_memory");
        }
        boolean named = options.getToolNames() != null && options.getToolNames().contains(TOOL_NAME);
        boolean callback = options.getToolCallbacks() != null && options.getToolCallbacks().stream()
                .anyMatch(item -> TOOL_NAME.equals(item.getToolDefinition().name()));
        if (!named && !callback) {
            throw new IllegalStateException("Mock Provider 要求可见工具 save_memory");
        }
    }

    private static void validateToolResult(List<org.springframework.ai.chat.messages.Message> messages,
            ToolResponseMessage result, String fact) {
        var assistant = messages.stream().filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .filter(item -> !item.getToolCalls().isEmpty())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("Mock Provider 工具结果缺少对应调用"));
        var expected = firstRound(fact).getToolCalls().getFirst();
        var actualCall = assistant.getToolCalls().getFirst();
        if (!expected.id().equals(actualCall.id()) || !TOOL_NAME.equals(actualCall.name())
                || result.getResponses().size() != 1) {
            throw new IllegalStateException("Mock Provider 收到不匹配的工具结果");
        }
        var actual = result.getResponses().getFirst();
        String data = actual.responseData();
        if (!expected.id().equals(actual.id()) || !TOOL_NAME.equals(actual.name())
                || data == null || !data.replace(" ", "").contains("\"success\":true")) {
            throw new IllegalStateException("Mock Provider 收到不匹配的工具结果");
        }
    }

    private ChatResponse response(AssistantMessage message, int promptTokens, int completionTokens) {
        var metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(promptTokens, completionTokens,
                        promptTokens + completionTokens))
                .build();
        return new ChatResponse(List.of(new Generation(message)), metadata);
    }
}
