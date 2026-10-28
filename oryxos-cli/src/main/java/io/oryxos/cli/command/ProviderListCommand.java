package io.oryxos.cli.command;

import io.oryxos.cli.CliRuntime;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "list", description = "列出 Provider", mixinStandardHelpOptions = true)
public final class ProviderListCommand implements Callable<Integer> {
    private final CliRuntime runtime;

    private final Path workspace;

    private final PrintWriter output;

    public ProviderListCommand(CliRuntime runtime, Path workspace, PrintWriter output) {
        this.runtime = runtime;
        this.workspace = workspace;
        this.output = new PrintWriter(output, true);
    }

    @Override
    public Integer call() {
        var providers = runtime.providers(workspace).stream()
                .sorted(Comparator.comparing(info -> info.name()))
                .toList();
        if (providers.isEmpty()) {
            output.println("未配置 Provider");
            return 0;
        }
        output.println("NAME\tTYPE\tMODEL\tBASE_URL");
        providers.forEach(info -> output.println(cell(info.name()) + "\t" + cell(info.type())
                + "\t" + cell(info.model()) + "\t" + cell(info.baseUrl())));
        return 0;
    }

    private static String cell(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\t\\r\\n]", " ");
    }
}
