package io.oryxos.tool.sandbox;

public sealed interface SandboxAction permits FileAccessAction, HttpRequestAction, ShellExecuteAction {
}
