package io.oryxos.tool.sandbox;

import java.net.URI;
import java.util.Objects;

public record SandboxAction(ActionType type, URI target) {
    public SandboxAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
    }
}
