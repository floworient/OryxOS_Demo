package io.oryxos.core.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotifyChannelServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-28T08:00:00Z");

    private final InMemoryRegistry registry = new InMemoryRegistry();

    private final NotifyChannelService service = serviceAt(CREATED_AT);

    @Test
    void createsWebhookChannelWithDefensiveConfiguration() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("format", "plain");

        NotifyChannel channel = service.create(new NotifyChannelService.CreateCommand(
                "team-webhook", "webhook", "${TEAM_WEBHOOK_URL}", "团队通知", config));
        config.put("format", "changed");

        assertThat(channel.name()).isEqualTo("team-webhook");
        assertThat(channel.type()).isEqualTo("webhook");
        assertThat(channel.url()).isEqualTo("${TEAM_WEBHOOK_URL}");
        assertThat(channel.config()).containsEntry("format", "plain");
        assertThat(channel.createdAt()).isEqualTo(CREATED_AT);
        assertThat(channel.updatedAt()).isEqualTo(CREATED_AT);
        assertThatThrownBy(() -> channel.config().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "CON", "nul.txt", "../escape", "C:\\temp", "a/b"})
    void rejectsUnsafeChannelNames(String name) {
        assertThatThrownBy(() -> service.create(command(name, "https://example.com/hook")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("渠道名称");
    }

    @Test
    void rejectsUnsupportedTypeMalformedUrlAndReservedConfiguration() {
        assertThatThrownBy(() -> service.create(new NotifyChannelService.CreateCommand(
                "team", "feishu", "https://example.com/hook", null, Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("渠道类型");
        assertThatThrownBy(() -> service.create(command("team", "${TEAM-WEBHOOK}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");
        assertThatThrownBy(() -> service.create(new NotifyChannelService.CreateCommand(
                "team", "webhook", "https://example.com/hook", null, Map.of("url", "override"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void resolvesFirstChannelByCreationTimeThenName() {
        registry.channels.put("later", channel("later", CREATED_AT.plusSeconds(10)));
        registry.channels.put("zeta", channel("zeta", CREATED_AT));
        registry.channels.put("alpha", channel("alpha", CREATED_AT));

        assertThat(service.resolve(null).name()).isEqualTo("alpha");
        assertThat(service.resolve(" ").name()).isEqualTo("alpha");
        assertThat(service.list()).extracting(NotifyChannel::name)
                .containsExactly("alpha", "zeta", "later");
    }

    @Test
    void updatesMutableFieldsButPreservesCreationTime() {
        service.create(command("team", "https://example.com/old"));
        NotifyChannelService later = serviceAt(CREATED_AT.plusSeconds(60));

        NotifyChannel updated = later.update("team", new NotifyChannelService.UpdateCommand(
                "webhook", "${NEW_WEBHOOK_URL}", "新描述", Map.of("format", "plain")));

        assertThat(updated.createdAt()).isEqualTo(CREATED_AT);
        assertThat(updated.updatedAt()).isEqualTo(CREATED_AT.plusSeconds(60));
        assertThat(updated.url()).isEqualTo("${NEW_WEBHOOK_URL}");
        assertThat(updated.description()).isEqualTo("新描述");
    }

    @Test
    void rejectsDuplicateAndMissingChannelsExplicitly() {
        service.create(command("team", "https://example.com/hook"));

        assertThatThrownBy(() -> service.create(command("team", "https://example.com/other")))
                .isInstanceOf(NotifyChannelConflictException.class);
        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(NotifyChannelNotFoundException.class);
        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(NotifyChannelNotFoundException.class);
        service.delete("team");
        assertThatThrownBy(() -> service.resolve(null))
                .isInstanceOf(NotifyChannelNotFoundException.class)
                .hasMessageContaining("未配置");
    }

    private NotifyChannelService serviceAt(Instant instant) {
        return new NotifyChannelService(registry, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static NotifyChannelService.CreateCommand command(String name, String url) {
        return new NotifyChannelService.CreateCommand(name, "webhook", url, null, Map.of());
    }

    private static NotifyChannel channel(String name, Instant createdAt) {
        return new NotifyChannel(name, "webhook", "https://example.com/" + name,
                null, Map.of(), createdAt, createdAt);
    }

    private static final class InMemoryRegistry implements NotifyChannelRegistry {
        private final Map<String, NotifyChannel> channels = new LinkedHashMap<>();

        @Override
        public NotifyChannel create(NotifyChannel channel) {
            if (channels.putIfAbsent(channel.name(), channel) != null) {
                throw new NotifyChannelConflictException("通知渠道名称已存在");
            }
            return channel;
        }

        @Override
        public NotifyChannel update(NotifyChannel channel) {
            channels.put(channel.name(), channel);
            return channel;
        }

        @Override
        public Optional<NotifyChannel> find(String name) {
            return Optional.ofNullable(channels.get(name));
        }

        @Override
        public List<NotifyChannel> list() {
            return new ArrayList<>(channels.values());
        }

        @Override
        public void delete(String name) {
            channels.remove(name);
        }
    }
}
