package io.oryxos.core.notify;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record NotifyChannel(
        String name,
        String type,
        String url,
        String description,
        Map<String, String> config,
        Instant createdAt,
        Instant updatedAt) {

    public NotifyChannel {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(url, "url");
        config = Map.copyOf(Objects.requireNonNull(config, "config"));
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("更新时间不得早于创建时间");
        }
    }
}
