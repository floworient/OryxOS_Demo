package io.oryxos.cli.command;

import io.oryxos.cli.CliRuntime;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

@Command(name = "serve", description = "启动 HTTP 服务", mixinStandardHelpOptions = true)
public final class ServeCommand implements Callable<Integer> {
    private final CliRuntime runtime;

    private final Path workspace;

    @Option(names = "--port", defaultValue = "8080", converter = PortConverter.class,
            description = "监听端口（1..65535）")
    private int port;

    public ServeCommand(CliRuntime runtime, Path workspace) {
        this.runtime = runtime;
        this.workspace = workspace;
    }

    @Override
    public Integer call() {
        runtime.serve(workspace, port);
        return 0;
    }

    public static final class PortConverter implements ITypeConverter<Integer> {
        @Override
        public Integer convert(String value) {
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            }
            catch (NumberFormatException exception) {
                throw new TypeConversionException("端口必须是 1..65535 的整数");
            }
            if (parsed < 1 || parsed > 65_535) {
                throw new TypeConversionException("端口必须在 1..65535 之间");
            }
            return parsed;
        }
    }
}
