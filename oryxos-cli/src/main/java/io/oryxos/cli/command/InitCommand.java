package io.oryxos.cli.command;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "init", description = "初始化 OryxOS 工作区", mixinStandardHelpOptions = true)
public final class InitCommand implements Callable<Integer> {
    private final Runnable action;

    public InitCommand(Runnable action) {
        this.action = action;
    }

    @Override
    public Integer call() {
        action.run();
        return 0;
    }
}
