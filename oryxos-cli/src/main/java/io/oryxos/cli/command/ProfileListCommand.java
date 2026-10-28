package io.oryxos.cli.command;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "list", description = "列出活动 Agent", mixinStandardHelpOptions = true)
public final class ProfileListCommand implements Callable<Integer> {
    private final Runnable action;

    public ProfileListCommand(Runnable action) {
        this.action = action;
    }

    @Override
    public Integer call() {
        action.run();
        return 0;
    }
}
