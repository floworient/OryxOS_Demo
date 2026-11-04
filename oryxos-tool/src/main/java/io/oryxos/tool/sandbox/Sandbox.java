package io.oryxos.tool.sandbox;

import java.net.URI;
import java.nio.file.Path;

@FunctionalInterface
public interface Sandbox {
    URI enforce(HttpRequestAction action);

    default Path enforce(FileAccessAction action) {
        throw new SandboxViolationException(SandboxViolationException.FILE_DENIED);
    }

    default Path enforce(ShellExecuteAction action) {
        throw new SandboxViolationException(SandboxViolationException.SHELL_DENIED);
    }
}
