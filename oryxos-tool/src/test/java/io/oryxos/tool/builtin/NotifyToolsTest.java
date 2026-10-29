package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.core.notify.NotifyChannelRegistry;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.tool.notify.NotifyAdapterRegistry;
import io.oryxos.tool.notify.NotifyChannelAdapter;
import io.oryxos.tool.notify.NotifyTarget;
import io.oryxos.tool.notify.NotifyTargetResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotifyToolsTest {

    @Test
    void resolvesSandboxesThenSendsUsingDefaultChannel() {
        List<String> events = new ArrayList<>();
        NotifyChannel channel = channel();
        NotifyChannelService service = new NotifyChannelService(registry(channel),
                Clock.fixed(channel.createdAt(), ZoneOffset.UTC));
        NotifyTargetResolver resolver = new NotifyTargetResolver(System::getenv, action -> {
            events.add("sandbox");
            return action.target();
        });
        NotifyChannelAdapter adapter = new NotifyChannelAdapter() {
            @Override
            public String channelType() {
                return "webhook";
            }

            @Override
            public void send(NotifyTarget target, String content) {
                events.add("send:" + content);
            }
        };
        NotifyTools tool = new NotifyTools(service, resolver, new NotifyAdapterRegistry(List.of(adapter)));

        ToolResult result = tool.execute("{\"content\":\"hello\",\"channel\":\" \"}");

        assertThat(result).isEqualTo(new ToolResult(true, "已推送", null, false));
        assertThat(events).containsExactly("sandbox", "send:hello");
        assertThat(tool.getInputSchema()).contains("\"additionalProperties\":false")
                .contains("\"required\":[\"content\"]");
    }

    @Test
    void rejectsMalformedOrUnknownInputWithoutRetry() {
        NotifyChannelService service = new NotifyChannelService(registry(), Clock.systemUTC());
        NotifyTools tool = new NotifyTools(service,
                new NotifyTargetResolver(System::getenv, action -> action.target()),
                new NotifyAdapterRegistry(List.of()));

        for (String input : List.of("{}", "{\"content\":\" \"}",
                "{\"content\":\"x\",\"extra\":1}", "{\"content\":\"x\"} trailing")) {
            ToolResult result = tool.execute(input);
            assertThat(result.success()).isFalse();
            assertThat(result.retryable()).isFalse();
            assertThat(result.content()).isEmpty();
        }
    }

    private static NotifyChannel channel() {
        Instant now = Instant.parse("2026-10-28T08:00:00Z");
        return new NotifyChannel("team", "webhook", "https://example.com/hook",
                null, Map.of(), now, now);
    }

    private static NotifyChannelRegistry registry(NotifyChannel... channels) {
        Map<String, NotifyChannel> values = new java.util.LinkedHashMap<>();
        for (NotifyChannel channel : channels) {
            values.put(channel.name(), channel);
        }
        return new NotifyChannelRegistry() {
            @Override
            public NotifyChannel create(NotifyChannel channel) {
                values.put(channel.name(), channel);
                return channel;
            }

            @Override
            public NotifyChannel update(NotifyChannel channel) {
                values.put(channel.name(), channel);
                return channel;
            }

            @Override
            public Optional<NotifyChannel> find(String name) {
                return Optional.ofNullable(values.get(name));
            }

            @Override
            public List<NotifyChannel> list() {
                return List.copyOf(values.values());
            }

            @Override
            public void delete(String name) {
                values.remove(name);
            }
        };
    }
}
