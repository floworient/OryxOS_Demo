package io.oryxos.cli;

import io.oryxos.cli.command.ChatCommand;
import io.oryxos.cli.command.GatewayCommand;
import io.oryxos.cli.command.InitCommand;
import io.oryxos.cli.command.ProfileCommand;
import io.oryxos.cli.command.ProfileCreateCommand;
import io.oryxos.cli.command.ProfileDeleteCommand;
import io.oryxos.cli.command.ProfileListCommand;
import io.oryxos.cli.command.ProfileShowCommand;
import io.oryxos.cli.command.ProviderCommand;
import io.oryxos.cli.command.ProviderListCommand;
import io.oryxos.cli.command.ServeCommand;
import io.oryxos.cli.command.SessionCommand;
import io.oryxos.cli.command.SessionListCommand;
import io.oryxos.cli.command.StatusCommand;
import io.oryxos.cli.command.ToolCommand;
import io.oryxos.cli.command.ToolListCommand;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** OryxOS Picocli 根入口。 */
@Command(name = "oryxos", mixinStandardHelpOptions = true,
        versionProvider = OryxOsCli.VersionProvider.class,
        description = "OryxOS command line interface")
public final class OryxOsCli implements Runnable {
    private static final String DEVELOPMENT_VERSION = "0.1.0-SNAPSHOT";

    private final CommandLine commandLine;

    public OryxOsCli(CliRuntime runtime, Path workspace, Reader input, PrintWriter output,
            PrintWriter error, Supplier<String> userId) {
        commandLine = new CommandLine(this);
        var failureHandler = new CliFailureHandler();
        commandLine.setExecutionExceptionHandler(failureHandler);
        commandLine.setParameterExceptionHandler(failureHandler);
        var workspaceService = new WorkspaceService(workspace, Clock.systemUTC());

        commandLine.addSubcommand(new InitCommand(() -> printInit(output, workspaceService.initialize())));
        commandLine.addSubcommand(new StatusCommand(() -> printStatus(output, workspaceService.status())));
        commandLine.addSubcommand(new ChatCommand(runtime, workspace, input, output, userId));
        commandLine.addSubcommand(new ServeCommand(runtime, workspace));
        commandLine.addSubcommand(new GatewayCommand(runtime, workspace));

        var profile = new CommandLine(new ProfileCommand());
        profile.addSubcommand(new ProfileListCommand(() -> {
            var profiles = workspaceService.listProfiles();
            if (profiles.isEmpty()) {
                output.println("未找到 Agent");
            } else {
                profiles.forEach(output::println);
            }
        }));
        profile.addSubcommand(new ProfileCreateCommand(name -> {
            workspaceService.createProfile(name);
            output.println("已创建 Agent: " + name);
        }));
        profile.addSubcommand(new ProfileShowCommand(name -> {
            output.print(workspaceService.showProfile(name));
            output.flush();
        }));
        profile.addSubcommand(new ProfileDeleteCommand(name -> {
            Path archived = workspaceService.archiveProfile(name);
            output.println("已归档 Agent: "
                    + workspace.relativize(archived).toString().replace('\\', '/'));
        }));
        commandLine.addSubcommand(profile);

        var provider = new CommandLine(new ProviderCommand());
        provider.addSubcommand(new ProviderListCommand(runtime, workspace, output));
        commandLine.addSubcommand(provider);
        var tool = new CommandLine(new ToolCommand());
        tool.addSubcommand(new ToolListCommand(runtime, workspace, output));
        commandLine.addSubcommand(tool);
        var session = new CommandLine(new SessionCommand());
        session.addSubcommand(new SessionListCommand(runtime, workspace, output));
        commandLine.addSubcommand(session);
        configureOutput(commandLine, output, error);
    }

    public int execute(String... args) {
        return commandLine.execute(args);
    }

    @Override
    public void run() {
        commandLine.usage(commandLine.getOut());
    }

    private static void printInit(PrintWriter output, WorkspaceService.InitResult result) {
        output.println("workspace=" + result.root());
        output.println("created=" + result.created() + " skipped=" + result.skipped());
    }

    private static void printStatus(PrintWriter output, WorkspaceService.Status status) {
        output.println("root=" + status.root());
        output.println("initialized=" + status.initialized());
        output.println("agentCount=" + status.agentCount());
        output.println("database=" + (status.databaseExists() ? "present" : "missing"));
    }

    private static void configureOutput(CommandLine current, PrintWriter output, PrintWriter error) {
        current.setOut(output);
        current.setErr(error);
        current.getSubcommands().values().forEach(child -> configureOutput(child, output, error));
    }

    static String versionLine() {
        String implementationVersion = OryxOsCli.class.getPackage().getImplementationVersion();
        String version = implementationVersion == null ? DEVELOPMENT_VERSION : implementationVersion;
        return "OryxOS " + version;
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {versionLine()};
        }
    }
}

