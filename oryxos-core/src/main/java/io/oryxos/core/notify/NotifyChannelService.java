package io.oryxos.core.notify;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NotifyChannelService {

    private static final Comparator<NotifyChannel> DEFAULT_ORDER = Comparator
            .comparing(NotifyChannel::createdAt)
            .thenComparing(NotifyChannel::name);

    private final NotifyChannelRegistry registry;

    private final Clock clock;

    public NotifyChannelService(NotifyChannelRegistry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
    }

    public NotifyChannel create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        NotifyChannelValidator.validate(command.name(), command.type(), command.url(),
                command.description(), command.config());
        if (registry.find(command.name()).isPresent()) {
            throw new NotifyChannelConflictException("通知渠道名称已存在");
        }
        Instant now = clock.instant();
        return registry.create(new NotifyChannel(command.name(), command.type(), command.url(),
                command.description(), command.config(), now, now));
    }

    public NotifyChannel update(String name, UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        NotifyChannel existing = get(name);
        NotifyChannelValidator.validate(name, command.type(), command.url(),
                command.description(), command.config());
        return registry.update(new NotifyChannel(name, command.type(), command.url(),
                command.description(), command.config(), existing.createdAt(), clock.instant()));
    }

    public NotifyChannel get(String name) {
        NotifyChannelValidator.validateName(name);
        return registry.find(name)
                .orElseThrow(() -> new NotifyChannelNotFoundException("通知渠道不存在"));
    }

    public List<NotifyChannel> list() {
        return registry.list().stream().sorted(DEFAULT_ORDER).toList();
    }

    public NotifyChannel resolve(String name) {
        if (name != null && !name.isBlank()) {
            return get(name);
        }
        return list().stream().findFirst()
                .orElseThrow(() -> new NotifyChannelNotFoundException("未配置通知渠道"));
    }

    public void delete(String name) {
        get(name);
        registry.delete(name);
    }

    public record CreateCommand(String name, String type, String url, String description,
            Map<String, String> config) {
        public CreateCommand {
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }

    public record UpdateCommand(String type, String url, String description,
            Map<String, String> config) {
        public UpdateCommand {
            config = config == null ? Map.of() : Map.copyOf(config);
        }
    }
}
