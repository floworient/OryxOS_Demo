package io.oryxos.web.notify;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.notify.NotifyChannel;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotifyChannelMaskerTest {

    private final NotifyChannelMasker masker = new NotifyChannelMasker();

    @Test
    void masksSecretsButPreservesEnvironmentPlaceholders() {
        NotifyChannel real = channel("https://example.com/private-1234",
                Map.of("ApiToken", "abcdef", "format", "plain"));

        NotifyChannelView view = masker.view(real);

        assertThat(view.url()).isEqualTo("****1234");
        assertThat(view.config()).containsEntry("ApiToken", "****cdef")
                .containsEntry("format", "plain");
        assertThat(masker.view(channel("${TEAM_WEBHOOK_URL}", Map.of())).url())
                .isEqualTo("${TEAM_WEBHOOK_URL}");
    }

    @Test
    void mergesMaskedUpdateWithoutLosingExistingSecrets() {
        NotifyChannel existing = channel("https://example.com/private-1234",
                Map.of("token", "abcdef", "format", "plain"));

        NotifyChannelMasker.MergedUpdate merged = masker.merge(existing, null,
                Map.of("token", "****cdef", "format", "markdown"));

        assertThat(merged.url()).isEqualTo(existing.url());
        assertThat(merged.config()).containsEntry("token", "abcdef")
                .containsEntry("format", "markdown");
    }

    private static NotifyChannel channel(String url, Map<String, String> config) {
        Instant now = Instant.parse("2026-10-28T08:00:00Z");
        return new NotifyChannel("team", "webhook", url, "团队通知", config, now, now);
    }
}
