package io.oryxos.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WhitelistSandboxTest {

    @Test
    void allowsExactAndLabelBoundedSubdomain() {
        Sandbox sandbox = new WhitelistSandbox(Set.of("example.com", "*.hooks.example.org"));
        assertThat(sandbox.enforce(action("https://EXAMPLE.com/a")).getHost()).isEqualTo("example.com");
        assertThat(sandbox.enforce(action("https://a.hooks.example.org/a")).getHost())
                .isEqualTo("a.hooks.example.org");
    }

    @Test
    void defaultsToDenyAndRejectsUnsafeUris() {
        assertThatThrownBy(() -> new WhitelistSandbox(Set.of()).enforce(action("https://example.com")))
                .isInstanceOf(SecurityException.class);
        Sandbox sandbox = new WhitelistSandbox(Set.of("*.example.com"));
        assertThatThrownBy(() -> sandbox.enforce(action("https://example.com")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(action("https://evil-example.com")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(action("https://u:p@a.example.com")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(action("https://a.example.com/#x")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(action("file:///tmp/x"))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(action("https://a.example.com:99999/x")))
                .isInstanceOf(SecurityException.class);
    }

    private static SandboxAction action(String uri) {
        return new SandboxAction(ActionType.HTTP_REQUEST, URI.create(uri));
    }
}
