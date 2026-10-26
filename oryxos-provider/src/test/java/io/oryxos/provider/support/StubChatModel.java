package io.oryxos.provider.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

public final class StubChatModel implements ChatModel {

    private final ChatResponse response;

    private final RuntimeException failure;

    private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

    private final AtomicInteger calls = new AtomicInteger();

    private StubChatModel(ChatResponse response, RuntimeException failure) {
        this.response = response;
        this.failure = failure;
    }

    public static StubChatModel text(String content, String model) {
        var metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(7, 5, 12))
                .build();
        return new StubChatModel(
                new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata), null);
    }

    public static StubChatModel empty(String model) {
        return text("   ", model);
    }

    public static StubChatModel toolCall(String model) {
        var message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "weather", "{\"city\":\"杭州\"}")))
                .build();
        Usage unknownUsage = new Usage() {
            @Override
            public Integer getPromptTokens() {
                return null;
            }

            @Override
            public Integer getCompletionTokens() {
                return null;
            }

            @Override
            public Integer getTotalTokens() {
                return null;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
        return new StubChatModel(new ChatResponse(
                List.of(new Generation(message)),
                ChatResponseMetadata.builder().model(model).usage(unknownUsage).build()), null);
    }

    public static StubChatModel failure(RuntimeException failure) {
        return new StubChatModel(null, failure);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt.set(prompt);
        calls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    public Prompt lastPrompt() {
        return lastPrompt.get();
    }

    public int calls() {
        return calls.get();
    }
}
