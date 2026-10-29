package io.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotifyAdapterRegistryTest {

    @Test
    void routesByExplicitTypeAndDefensivelyCopies() {
        NotifyChannelAdapter webhook = adapter("webhook");
        List<NotifyChannelAdapter> source = new ArrayList<>(List.of(webhook, adapter("mail")));
        NotifyAdapterRegistry registry = new NotifyAdapterRegistry(source);
        source.clear();

        assertThat(registry.require("webhook")).isSameAs(webhook);
        assertThatThrownBy(() -> registry.require("unknown"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("url");
    }

    @Test
    void rejectsDuplicateAndBlankTypesImmediately() {
        assertThatThrownBy(() -> new NotifyAdapterRegistry(List.of(adapter("x"), adapter("x"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotifyAdapterRegistry(List.of(adapter(" "))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NotifyChannelAdapter adapter(String type) {
        return new NotifyChannelAdapter() {
            @Override
            public String channelType() {
                return type;
            }

            @Override
            public void send(NotifyTarget target, String content) {
            }
        };
    }
}
