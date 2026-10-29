package io.oryxos.tool.notify;

import java.util.Map;
import java.util.Objects;

public record NotifyTarget(String channelType, Map<String, String> config) {
    public NotifyTarget {
        Objects.requireNonNull(channelType, "channelType");
        config = Map.copyOf(Objects.requireNonNull(config, "config"));
    }
}
