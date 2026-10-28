package io.oryxos.channel.cli;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UncheckedIOException;

/** 终端交互渠道；输入输出流的生命周期由调用方管理。 */
public final class CliChannel {
    private final AgentService agentService;

    private final SessionManager sessionManager;

    private final Reader input;

    private final PrintWriter output;

    private final String userId;

    public CliChannel(AgentService agentService, SessionManager sessionManager, Reader input,
            PrintWriter output, String userId) {
        this.agentService = agentService;
        this.sessionManager = sessionManager;
        this.input = input;
        this.output = new PrintWriter(output);
        this.userId = userId;
    }

    public void run(String profileName) {
        Session session = sessionManager.getOrCreate("cli", userId, profileName);
        BufferedReader reader = input instanceof BufferedReader buffered
                ? buffered : new BufferedReader(input);
        while (true) {
            output.print("> ");
            output.flush();
            String line = readLine(reader);
            if (line == null || "/quit".equals(line.trim())) {
                return;
            }
            if (line.isBlank()) {
                continue;
            }
            output.println(agentService.process(session, line));
            output.flush();
        }
    }

    private static String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        }
        catch (IOException exception) {
            throw new UncheckedIOException("读取 CLI 输入失败", exception);
        }
    }
}
