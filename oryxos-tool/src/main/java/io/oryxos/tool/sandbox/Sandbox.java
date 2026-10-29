package io.oryxos.tool.sandbox;

import java.net.URI;

@FunctionalInterface
public interface Sandbox {
    URI enforce(SandboxAction action);
}
