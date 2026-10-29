package io.oryxos.tool.notify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NotifyAdapterRegistry {

    private final Map<String, NotifyChannelAdapter> adapters;

    public NotifyAdapterRegistry(List<NotifyChannelAdapter> adapters) {
        Map<String, NotifyChannelAdapter> indexed = new LinkedHashMap<>();
        for (NotifyChannelAdapter adapter : List.copyOf(adapters)) {
            if (adapter == null || adapter.channelType() == null || adapter.channelType().isBlank()) {
                throw new IllegalArgumentException("通知 Adapter 类型不能为空");
            }
            if (indexed.putIfAbsent(adapter.channelType(), adapter) != null) {
                throw new IllegalArgumentException("通知 Adapter 类型重复");
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    public NotifyChannelAdapter require(String channelType) {
        NotifyChannelAdapter adapter = adapters.get(channelType);
        if (adapter == null) {
            throw new IllegalArgumentException("未注册该类型的通知 Adapter");
        }
        return adapter;
    }
}
