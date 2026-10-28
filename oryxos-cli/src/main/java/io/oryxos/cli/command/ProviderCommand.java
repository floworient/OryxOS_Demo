package io.oryxos.cli.command;

import picocli.CommandLine.Command;

@Command(name = "provider", description = "查询模型 Provider", mixinStandardHelpOptions = true)
public final class ProviderCommand extends HelpGroupCommand { }
