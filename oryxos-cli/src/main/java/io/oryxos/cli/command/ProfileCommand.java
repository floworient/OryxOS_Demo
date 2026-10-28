package io.oryxos.cli.command;

import picocli.CommandLine.Command;

@Command(name = "profile", description = "管理 Agent Profile", mixinStandardHelpOptions = true)
public final class ProfileCommand extends HelpGroupCommand { }
