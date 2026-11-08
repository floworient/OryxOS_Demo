package io.oryxos.core.session;

import java.time.Instant;
import java.util.Objects;

/** 不含消息正文的有界会话查询视图。 */
public record SessionSummary(String id, String profileName, String channel, String userId,
        SessionStatus status, Instant createdAt, Instant lastActiveAt, int messageCount) {
    public SessionSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profileName, "profileName");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastActiveAt, "lastActiveAt");
        if (messageCount < 0) {
            throw new IllegalArgumentException("消息数量不能为负数");
        }
    }

    /** 兼容既有 CLI 测试夹具；新代码应提供完整摘要字段。 */
    public SessionSummary(String id, String profileName, String status, Instant lastActiveAt) {
        this(id, profileName, "unknown", "unknown",
                SessionStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT)),
                lastActiveAt, lastActiveAt, 0);
    }
}
