package io.oryxos.cli.command;

import io.oryxos.cli.CliRuntime;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "gateway", description = "启动聚合运行时", mixinStandardHelpOptions = true)
public final class GatewayCommand implements Callable<Integer> {
    private final CliRuntime runtime;

    private final Path workspace;

    public GatewayCommand(CliRuntime runtime, Path workspace) {
        this.runtime = runtime;
        this.workspace = workspace;
    }

    @Override
    public Integer call() {
        runtime.gateway(workspace);
        return 0;
    }
}
