package io.oryxos.cli.command;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "status", description = "查看工作区状态", mixinStandardHelpOptions = true)
public final class StatusCommand implements Callable<Integer> {
    private final Runnable action;

    public StatusCommand(Runnable action) {
        this.action = action;
    }

    @Override
    public Integer call() {
        action.run();
        return 0;
    }
}
