package io.oryxos.cli.command;

import io.oryxos.cli.CliRuntime;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "list", description = "列出 Tool", mixinStandardHelpOptions = true)
public final class ToolListCommand implements Callable<Integer> {
    private final CliRuntime runtime;

    private final Path workspace;

    private final PrintWriter output;

    public ToolListCommand(CliRuntime runtime, Path workspace, PrintWriter output) {
        this.runtime = runtime;
        this.workspace = workspace;
        this.output = new PrintWriter(output, true);
    }

    @Override
    public Integer call() {
        var tools = runtime.tools(workspace).stream()
                .sorted(Comparator.comparing(info -> info.name()))
                .toList();
        if (tools.isEmpty()) {
            output.println("未注册 Tool");
            return 0;
        }
        output.println("NAME\tDESCRIPTION");
        tools.forEach(info -> output.println(cell(info.name()) + "\t" + cell(info.description())));
        return 0;
    }

    private static String cell(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\t\\r\\n]", " ");
    }
}
