package io.oryxos.cli.command;

import picocli.CommandLine.Command;

@Command(name = "tool", description = "查询可用 Tool", mixinStandardHelpOptions = true)
public final class ToolCommand extends HelpGroupCommand { }
