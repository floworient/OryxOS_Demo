package io.oryxos.cli.command;

import java.util.concurrent.Callable;
import picocli.CommandLine;

abstract class HelpGroupCommand implements Callable<Integer> {
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public final Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }
}
