package io.oryxos.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.chat.messages.Message;

public record SessionDetails(String id, String channel, String userId, String profileName,
        SessionStatus status, Instant createdAt, Instant lastActiveAt,
        Instant archivedAt, List<Message> messages) {

    public SessionDetails {
        id = canonicalId(id);
        channel = required(channel, "会话渠道不能为空");
        userId = required(userId, "会话用户不能为空");
        profileName = required(profileName, "Agent 名称不能为空");
        status = Objects.requireNonNull(status, "会话状态不能为空");
        createdAt = Objects.requireNonNull(createdAt, "会话创建时间不能为空");
        lastActiveAt = Objects.requireNonNull(lastActiveAt, "会话最后活跃时间不能为空");
        if (lastActiveAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("会话最后活跃时间不能早于创建时间");
        }
        if (status == SessionStatus.ACTIVE && archivedAt != null) {
            throw new IllegalArgumentException("活动会话不能包含归档时间");
        }
        if (status == SessionStatus.ARCHIVED
                && (archivedAt == null || archivedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("归档会话必须包含合法归档时间");
        }
        messages = Objects.requireNonNull(messages, "会话消息不能为空").stream()
                .map(Session::snapshotMessage).toList();
    }

    @Override
    public List<Message> messages() {
        return messages.stream().map(Session::snapshotMessage).toList();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String canonicalId(String value) {
        try {
            String canonical = UUID.fromString(required(value, "会话标识不能为空")).toString();
            if (!canonical.equals(value)) {
                throw new IllegalArgumentException("会话标识必须是规范 UUID");
            }
            return canonical;
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("会话标识必须是规范 UUID", exception);
        }
    }
}
