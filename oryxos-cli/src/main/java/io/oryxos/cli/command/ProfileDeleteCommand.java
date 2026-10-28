package io.oryxos.cli.command;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "delete", description = "归档 Agent", mixinStandardHelpOptions = true)
public final class ProfileDeleteCommand implements Callable<Integer> {
    private final Consumer<String> action;

    @Parameters(index = "0", paramLabel = "<name>", description = "Agent 名称")
    private String name;

    public ProfileDeleteCommand(Consumer<String> action) {
        this.action = action;
    }

    @Override
    public Integer call() {
        action.accept(name);
        return 0;
    }
}
