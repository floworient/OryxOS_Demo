package io.oryxos.web.notify;

import java.time.Instant;
import java.util.Map;

public record NotifyChannelView(String name, String type, String url, String description,
        Map<String, String> config, Instant createdAt, Instant updatedAt) {
    public NotifyChannelView {
        config = Map.copyOf(config);
    }
}
