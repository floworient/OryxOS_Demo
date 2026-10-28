package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

class BootCliChatTest {
    @TempDir
    Path workspace;

    @Test
    void chatUsesNoneContextSharedWorkspaceAndClosesItAfterOneRound() {
        TestApplication.closed.set(false);
        var output = new StringWriter();
        var runtime = new BootCliRuntime(TestApplication.class);

        runtime.chat(workspace, "weather", "alice", new StringReader("问题\n/quit\n"),
                new PrintWriter(output, true));

        assertThat(output.toString()).contains("回答");
        verify(TestApplication.service).process(TestApplication.session, "问题");
        assertThat(TestApplication.closed).isTrue();
    }

    @SpringBootConfiguration
    static class TestApplication {
        private static final AgentService service = mock(AgentService.class);

        private static final Session session = new Session("cli", "alice", "weather");

        private static final AtomicBoolean closed = new AtomicBoolean();

        static {
            when(service.process(session, "问题")).thenReturn("回答");
        }

        @Bean
        AgentService agentService() {
            return service;
        }

        @Bean
        SessionManager sessionManager() {
            return new SessionManager() {
                public void save(Session value) { }

                public Optional<Session> find(String id) {
                    return Optional.ofNullable(id.equals(session.id()) ? session : null);
                }

                public Session getOrCreate(String channel, String userId, String profileName) { return session; }

                public java.util.List<io.oryxos.core.session.SessionSummary> list() { return java.util.List.of(); }
            };
        }

        @Bean(destroyMethod = "run")
        Runnable closeProbe() {
            return () -> closed.set(true);
        }
    }
}
