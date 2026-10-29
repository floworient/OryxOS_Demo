package io.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.tool.sandbox.Sandbox;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotifyTargetResolverTest {

    @Test
    void resolvesEnvironmentThenUsesSandboxApprovedUri() {
        Sandbox sandbox = action -> URI.create("https://approved.example/path");
        NotifyTargetResolver resolver = new NotifyTargetResolver(
                key -> "HOOK_URL".equals(key) ? "https://raw.example/path" : null, sandbox);

        NotifyTarget target = resolver.resolve(channel("${HOOK_URL}"));

        assertThat(target.channelType()).isEqualTo("webhook");
        assertThat(target.config()).containsEntry("url", "https://approved.example/path")
                .containsEntry("format", "plain");
    }

    @Test
    void rejectsMissingEnvironmentWithoutCallingSandbox() {
        int[] calls = {0};
        NotifyTargetResolver resolver = new NotifyTargetResolver(key -> null, action -> {
            calls[0]++;
            return action.target();
        });
        assertThatThrownBy(() -> resolver.resolve(channel("${MISSING}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("MISSING");
        assertThat(calls[0]).isZero();
    }

    private static NotifyChannel channel(String url) {
        Instant now = Instant.parse("2026-10-28T08:00:00Z");
        return new NotifyChannel("team", "webhook", url, null, Map.of("format", "plain"), now, now);
    }
}
