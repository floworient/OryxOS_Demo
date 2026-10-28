package io.oryxos;

import io.oryxos.boot.BootCliRuntime;
import io.oryxos.cli.CliRuntime;
import io.oryxos.cli.OryxOsCli;
import io.oryxos.cli.WorkspaceRootResolver;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OryxOsApplication {
    public static void main(String[] args) {
        Reader input = new InputStreamReader(System.in, StandardCharsets.UTF_8);
        PrintWriter output = new PrintWriter(
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        PrintWriter error = new PrintWriter(
                new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        var runtime = new BootCliRuntime(OryxOsApplication.class, output::println);
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "oryxos-shutdown"));
        int exit = execute(args, runtime, input, output, error, System.getProperties(),
                System.getenv(), Path.of(System.getProperty("user.dir", ".")),
                () -> System.getProperty("user.name"));
        if (exit != 0) {
            runtime.close();
            System.exit(exit);
        }
    }

    static int execute(String[] args, CliRuntime runtime, Reader input, PrintWriter output,
            PrintWriter error, Properties properties, Map<String, String> environment,
            Path userDirectory, Supplier<String> userId) {
        Path workspace = new WorkspaceRootResolver().resolve(
                properties, environment, userDirectory);
        return new OryxOsCli(runtime, workspace, input, output, error, userId).execute(args);
    }
}

