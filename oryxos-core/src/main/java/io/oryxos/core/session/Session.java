package io.oryxos.core.session;

import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.ToolResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.util.json.JsonParser;

public final class Session {

    private final String id;

    private final String channel;

    private final String userId;

    private final String profileName;

    private final List<Message> messages = new ArrayList<>();

    private final Instant createdAt;

    private Instant lastActiveAt;

    public Session(String channel, String userId, String profileName) {
        this(UUID.randomUUID().toString(), channel, userId, profileName,
                Instant.now(), Instant.now(), List.of());
    }

    public Session(String channel, String userId, String profileName, Instant createdAt,
            Instant lastActiveAt, List<Message> messages) {
        this(UUID.randomUUID().toString(), channel, userId, profileName,
                createdAt, lastActiveAt, messages);
    }

    public Session(String id, String channel, String userId, String profileName, Instant createdAt,
            Instant lastActiveAt, List<? extends Message> messages) {
        this.id = canonicalId(id);
        this.channel = required(channel);
        this.userId = required(userId);
        this.profileName = required(profileName);
        this.createdAt = java.util.Objects.requireNonNull(createdAt);
        this.lastActiveAt = java.util.Objects.requireNonNull(lastActiveAt);
        if (lastActiveAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("会话最后活跃时间不能早于创建时间");
        }
        this.messages.addAll(java.util.Objects.requireNonNull(messages).stream()
                .map(Session::snapshotMessage).toList());
    }

    private static String canonicalId(String value) {
        try {
            String canonical = UUID.fromString(required(value)).toString();
            if (!canonical.equals(value)) {
                throw new IllegalArgumentException("会话标识必须是规范 UUID");
            }
            return canonical;
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("会话标识必须是规范 UUID", exception);
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("会话身份字段不能为空");
        }
        return value;
    }

    public String id() {
        return id;
    }

    public String channel() {
        return channel;
    }

    public String userId() {
        return userId;
    }

    public String profileName() {
        return profileName;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastActiveAt() {
        return lastActiveAt;
    }

    public List<Message> messages() {
        return messages.stream().map(Session::snapshotMessage).toList();
    }

    public void append(String text) {
        add(new UserMessage(text));
    }

    public void append(ProviderResponse response) {
        add(AssistantMessage.builder().content(response.content())
                .toolCalls(response.toolCalls().stream().map(call -> new AssistantMessage.ToolCall(
                        call.id(), "function", call.name(), call.argumentsJson())).toList()).build());
    }

    public void appendAssistant(String text) {
        add(new AssistantMessage(text));
    }

    public void appendToolResult(ToolCallIntent call, ToolResult result) {
        add(ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(
                call.id(), call.name(), JsonParser.toJson(result)))).build());
    }

    private void add(Message message) {
        messages.add(message);
        lastActiveAt = Instant.now();
    }

    static Message snapshotMessage(Message message) {
        if (message instanceof UserMessage) {
            return new UserMessage(message.getText());
        }
        if (message instanceof AssistantMessage assistant) {
            String text = assistant.getText();
            return AssistantMessage.builder().content(text == null ? "" : text)
                    .toolCalls(List.copyOf(java.util.Objects.requireNonNull(
                            assistant.getToolCalls(), "工具调用集合不能为空"))).build();
        }
        if (message instanceof ToolResponseMessage tool) {
            return ToolResponseMessage.builder().responses(List.copyOf(java.util.Objects.requireNonNull(
                    tool.getResponses(), "工具结果集合不能为空"))).build();
        }
        throw new IllegalArgumentException("会话只能保存用户、助手和工具消息");
    }
}
