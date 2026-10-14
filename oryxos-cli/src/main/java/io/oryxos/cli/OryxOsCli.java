package io.oryxos.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Root command. Subcommands are added as their capabilities are implemented. */
@Command(
        name = "oryxos",
        mixinStandardHelpOptions = true,
        versionProvider = OryxOsCli.VersionProvider.class,
        description = "OryxOS command line interface")
public final class OryxOsCli implements Runnable {
    private static final String DEVELOPMENT_VERSION = "0.1.0-SNAPSHOT";

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OryxOsCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println(versionLine());
    }

    private static String versionLine() {
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

