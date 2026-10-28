package io.oryxos.cli.command;

import io.oryxos.cli.CliRuntime;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "list", description = "列出 Session", mixinStandardHelpOptions = true)
public final class SessionListCommand implements Callable<Integer> {
    private final CliRuntime runtime;

    private final Path workspace;

    private final PrintWriter output;

    public SessionListCommand(CliRuntime runtime, Path workspace, PrintWriter output) {
        this.runtime = runtime;
        this.workspace = workspace;
        this.output = new PrintWriter(output, true);
    }

    @Override
    public Integer call() {
        var sessions = runtime.sessions(workspace).stream()
                .sorted(Comparator.comparing(io.oryxos.cli.SessionInfo::lastActiveAt).reversed()
                        .thenComparing(io.oryxos.cli.SessionInfo::id))
                .toList();
        if (sessions.isEmpty()) {
            output.println("未找到 Session");
            return 0;
        }
        output.println("SESSION_ID\tPROFILE\tSTATUS\tLAST_ACTIVE_AT");
        sessions.forEach(info -> output.println(cell(info.id()) + "\t" + cell(info.profileName())
                + "\t" + cell(info.status()) + "\t" + info.lastActiveAt()));
        return 0;
    }

    private static String cell(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\t\\r\\n]", " ");
    }
}
