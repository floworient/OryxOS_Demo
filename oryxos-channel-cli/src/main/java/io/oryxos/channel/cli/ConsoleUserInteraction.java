package io.oryxos.channel.cli;

import io.oryxos.core.interaction.UserInteraction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

/** 使用当前 CliChannel 的输入输出完成单行问答，不拥有流生命周期。 */
public final class ConsoleUserInteraction implements UserInteraction {

    private final BufferedReader input;

    private final PrintWriter output;

    public ConsoleUserInteraction(BufferedReader input, PrintWriter output) {
        this.input = java.util.Objects.requireNonNull(input);
        this.output = java.util.Objects.requireNonNull(output);
    }

    @Override
    public synchronized String ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("交互问题不能为空");
        }
        output.print("? " + question + System.lineSeparator() + "> ");
        output.flush();
        try {
            String answer = input.readLine();
            if (answer == null) {
                throw new IllegalStateException("CLI 输入已结束");
            }
            return answer;
        }
        catch (IOException exception) {
            throw new UncheckedIOException("读取 CLI 回答失败", exception);
        }
    }
}
