package io.oryxos.core.notify;

import java.util.List;
import java.util.Optional;

public interface NotifyChannelRegistry {

    NotifyChannel create(NotifyChannel channel);

    NotifyChannel update(NotifyChannel channel);

    Optional<NotifyChannel> find(String name);

    List<NotifyChannel> list();

    void delete(String name);
}
