package io.oryxos.core.memory;

import java.time.Instant;
import java.util.Objects;

public record MemoryEntry(String id, MemoryOwner owner, MemoryScope scope,
        String content, Instant createdAt) {

    public MemoryEntry {
        id = requiredText(id, "记忆标识");
        owner = Objects.requireNonNull(owner, "记忆 owner 不能为空");
        scope = Objects.requireNonNull(scope, "记忆 scope 不能为空");
        content = requiredText(content, "记忆内容");
        createdAt = Objects.requireNonNull(createdAt, "记忆时间不能为空");
    }

    private static String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
