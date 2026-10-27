package io.oryxos.storage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.storage.entity.SessionEntity;
import io.oryxos.storage.repository.SessionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaSessionManager implements SessionManager {

    private final SessionRepository repository;

    public JpaSessionManager(SessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(Session session) {
        try {
            List<Map<String, Object>> data = session.messages().stream().map(JpaSessionManager::encode).toList();
            String json = JsonParser.toJson(data);
            decode(json);
            repository.saveAndFlush(new SessionEntity(session, json));
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("会话保存失败 sessionId=" + session.id(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> find(String sessionId) {
        try {
            return repository.findById(sessionId).map(entity -> entity.restore(decode(entity.messagesJson())));
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("会话恢复失败 sessionId=" + sessionId, exception);
        }
    }

    private static Map<String, Object> encode(Message message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", message.getText());
        if (message instanceof UserMessage) {
            data.put("role", "user");
        }
        else if (message instanceof AssistantMessage assistant) {
            data.put("role", "assistant");
            data.put("calls", assistant.getToolCalls());
        }
        else if (message instanceof ToolResponseMessage tool) {
            data.put("role", "tool");
            data.put("responses", tool.getResponses());
        }
        else {
            throw new IllegalArgumentException("不支持保存该消息角色");
        }
        return data;
    }

    private static List<Message> decode(String json) {
        Object value;
        try {
            value = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
        }
        catch (java.io.IOException exception) {
            throw new IllegalArgumentException("历史消息不是完整合法的 JSON", exception);
        }
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("历史必须是消息列表");
        }
        var messages = new ArrayList<Message>();
        Map<String, String> pending = new LinkedHashMap<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> data)) {
                throw new IllegalArgumentException("历史消息非法");
            }
            String role = string(data, "role");
            if (!"tool".equals(role) && !pending.isEmpty()) {
                throw new IllegalArgumentException("工具结果缺失");
            }
            switch (role) {
                case "user" -> messages.add(new UserMessage(string(data, "text")));
                case "assistant" -> {
                    var calls = new ArrayList<AssistantMessage.ToolCall>();
                    for (Map<?, ?> call : objects(data, "calls")) {
                        String id = nonblank(call, "id");
                        String name = nonblank(call, "name");
                        if (!"function".equals(string(call, "type")) || pending.putIfAbsent(id, name) != null) {
                            throw new IllegalArgumentException("工具调用标识或类型非法");
                        }
                        calls.add(new AssistantMessage.ToolCall(id, "function", name, string(call, "arguments")));
                    }
                    messages.add(AssistantMessage.builder().content(string(data, "text")).toolCalls(calls).build());
                }
                case "tool" -> {
                    var responses = new ArrayList<ToolResponseMessage.ToolResponse>();
                    for (Map<?, ?> result : objects(data, "responses")) {
                        String id = nonblank(result, "id");
                        String name = nonblank(result, "name");
                        if (!name.equals(pending.remove(id))) {
                            throw new IllegalArgumentException("工具调用与结果不匹配");
                        }
                        responses.add(new ToolResponseMessage.ToolResponse(id, name, string(result, "responseData")));
                    }
                    if (responses.isEmpty()) {
                        throw new IllegalArgumentException("工具结果不能为空");
                    }
                    messages.add(ToolResponseMessage.builder().responses(responses).build());
                }
                default -> throw new IllegalArgumentException("未知历史消息角色");
            }
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("工具结果缺失");
        }
        return List.copyOf(messages);
    }

    private static String string(Map<?, ?> data, String key) {
        if (!(data.get(key) instanceof String text)) {
            throw new IllegalArgumentException("历史消息字段非法: " + key);
        }
        return text;
    }

    private static String nonblank(Map<?, ?> data, String key) {
        String value = string(data, key);
        if (value.isBlank()) {
            throw new IllegalArgumentException("历史消息字段为空: " + key);
        }
        return value;
    }

    private static List<Map<?, ?>> objects(Map<?, ?> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("历史消息集合非法: " + key);
        }
        var result = new ArrayList<Map<?, ?>>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("历史消息集合元素非法: " + key);
            }
            result.add(map);
        }
        return result;
    }
}
