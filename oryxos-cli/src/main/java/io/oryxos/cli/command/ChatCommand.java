package io.oryxos.cli.command;

import io.oryxos.cli.CliRuntime;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "chat", description = "与 Agent 交互", mixinStandardHelpOptions = true)
public final class ChatCommand implements Callable<Integer> {
    private final CliRuntime runtime;

    private final Path workspace;

    private final Reader input;

    private final PrintWriter output;

    private final Supplier<String> userId;

    @Option(names = "--profile", defaultValue = "default", description = "Agent 名称")
    private String profileName;

    public ChatCommand(CliRuntime runtime, Path workspace, Reader input, PrintWriter output,
            Supplier<String> userId) {
        this.runtime = runtime;
        this.workspace = workspace;
        this.input = input;
        this.output = new PrintWriter(output, true);
        this.userId = userId;
    }

    @Override
    public Integer call() {
        String value = userId.get();
        runtime.chat(workspace, profileName, value == null || value.isBlank() ? "local-user" : value,
                input, output);
        return 0;
    }
}
