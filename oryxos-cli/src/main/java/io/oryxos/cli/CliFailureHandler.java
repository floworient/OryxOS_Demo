package io.oryxos.cli;

import java.util.regex.Pattern;
import picocli.CommandLine;

/** 把命令错误转换成稳定退出码和脱敏终端消息。 */
public final class CliFailureHandler
        implements CommandLine.IExecutionExceptionHandler, CommandLine.IParameterExceptionHandler {
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)(?:bearer\\s+)?[^\\s,;]+");

    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)((?:api[-_]?key|token)\\s*[:=]\\s*)[^\\s,;]+");

    @Override
    public int handleExecutionException(Exception exception, CommandLine commandLine,
            CommandLine.ParseResult parseResult) {
        commandLine.getErr().println("错误: " + safeMessage(exception));
        return 1;
    }

    @Override
    public int handleParseException(CommandLine.ParameterException exception, String[] args) {
        CommandLine commandLine = exception.getCommandLine();
        commandLine.getErr().println("错误: " + redact(exception.getMessage()));
        commandLine.usage(commandLine.getErr());
        return 2;
    }

    static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "命令执行失败";
        }
        String redacted = AUTHORIZATION.matcher(value).replaceAll("$1[REDACTED]");
        return CREDENTIAL.matcher(redacted).replaceAll("$1[REDACTED]");
    }

    private static String safeMessage(Exception exception) {
        Throwable current = exception;
        while ((current.getMessage() == null || current.getMessage().isBlank())
                && current.getCause() != null) {
            current = current.getCause();
        }
        return redact(current.getMessage());
    }
}
