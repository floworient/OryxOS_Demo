package io.oryxos.tool.sandbox;

import java.net.URI;
import java.util.Objects;

public record HttpRequestAction(URI target) implements SandboxAction {
    public HttpRequestAction {
        Objects.requireNonNull(target, "target");
    }
}
