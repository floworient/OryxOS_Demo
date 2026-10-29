package io.oryxos.tool.sandbox;

public record ShellExecuteAction(String executableAlias) implements SandboxAction {
    public ShellExecuteAction {
        if (executableAlias == null || executableAlias.isBlank()) {
            throw new IllegalArgumentException("Shell executable alias 不能为空");
        }
    }
}
