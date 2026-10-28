package io.oryxos.channel.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class CliChannelTest {
    @Test
    void questionsShareOneSessionWhileBlankQuitAndEofNeverInvokeAgent() {
        var service = mock(AgentService.class);
        var sessions = mock(SessionManager.class);
        var session = new Session("cli", "alice", "weather");
        when(sessions.getOrCreate("cli", "alice", "weather")).thenReturn(session);
        when(service.process(session, " 第一问 ")).thenReturn("回答一");
        when(service.process(session, "第二问")).thenReturn("回答二");
        var output = new StringWriter();

        new CliChannel(service, sessions,
                new StringReader("   \n 第一问 \n第二问\n/quit\n不应处理\n"),
                new PrintWriter(output, true), "alice").run("weather");

        verify(service).process(session, " 第一问 ");
        verify(service).process(session, "第二问");
        verify(service, times(2)).process(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(output.toString()).containsSubsequence("> ", "回答一", "> ", "回答二", "> ");
        assertThat(output.toString()).doesNotContain("不应处理");
    }

    @Test
    void immediateEofDoesNotInvokeAgentOrCloseCallerStreams() {
        var service = mock(AgentService.class);
        var sessions = mock(SessionManager.class);
        var reader = new TrackingReader("");
        var output = new StringWriter();
        var writer = new TrackingWriter(output);

        new CliChannel(service, sessions, reader, writer, "alice").run("weather");

        verify(service, never()).process(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(reader.closed).isFalse();
        assertThat(writer.closed).isFalse();
    }

    @Test
    void oneRoundFailurePropagatesWithoutRetryOrClosingCallerStreams() {
        var service = mock(AgentService.class);
        var sessions = mock(SessionManager.class);
        var session = new Session("cli", "alice", "weather");
        when(sessions.getOrCreate("cli", "alice", "weather")).thenReturn(session);
        when(service.process(session, "失败问题")).thenThrow(new IllegalStateException("provider timeout"));
        var reader = new TrackingReader("失败问题\n下一问\n");
        var output = new StringWriter();
        var writer = new TrackingWriter(output);

        assertThatThrownBy(() -> new CliChannel(service, sessions, reader, writer, "alice").run("weather"))
                .hasMessage("provider timeout");
        verify(service).process(session, "失败问题");
        assertThat(reader.closed).isFalse();
        assertThat(writer.closed).isFalse();
    }

    private static final class TrackingReader extends StringReader {
        private boolean closed;

        private TrackingReader(String value) {
            super(value);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingWriter extends PrintWriter {
        private boolean closed;

        private TrackingWriter(StringWriter delegate) {
            super(delegate, true);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
