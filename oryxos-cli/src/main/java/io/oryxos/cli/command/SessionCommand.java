package io.oryxos.cli.command;

import picocli.CommandLine.Command;

@Command(name = "session", description = "查询 Session", mixinStandardHelpOptions = true)
public final class SessionCommand extends HelpGroupCommand { }
