package io.oryxos.core.session;

import java.time.Instant;
import java.util.Objects;

/** 不含消息正文和用户标识的会话查询视图。 */
public record SessionSummary(String id, String profileName, String status, Instant lastActiveAt) {
    public SessionSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profileName, "profileName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(lastActiveAt, "lastActiveAt");
    }
}
