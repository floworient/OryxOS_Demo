package io.oryxos.cli.command;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "显示 Agent 定义", mixinStandardHelpOptions = true)
public final class ProfileShowCommand implements Callable<Integer> {
    private final Consumer<String> action;

    @Parameters(index = "0", paramLabel = "<name>", description = "Agent 名称")
    private String name;

    public ProfileShowCommand(Consumer<String> action) {
        this.action = action;
    }

    @Override
    public Integer call() {
        action.accept(name);
        return 0;
    }
}
