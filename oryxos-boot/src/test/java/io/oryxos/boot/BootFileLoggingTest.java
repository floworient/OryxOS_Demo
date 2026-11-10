package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

/** 验证长驻入口写工作区文件日志、查询入口不落盘。 */
class BootFileLoggingTest {
    @TempDir
    Path workspace;

    @AfterEach
    void releaseLogbackAppenders() {
        LogbackTestCleanup.releaseFileAppenders();
    }

    @Test
    void queryCommandsDoNotWriteWorkspaceLogs() throws Exception {
        Files.writeString(workspace.resolve("application.yaml"), """
                oryxos:
                  providers:
                    - name: mock
                      type: MOCK
                      model: mock-memory
                """);
        var runtime = new BootCliRuntime(ProviderQueryApplication.class, ignored -> { });

        var providers = runtime.providers(workspace);

        assertThat(providers).singleElement()
                .satisfies(info -> assertThat(info.name()).isEqualTo("mock"));
        assertThat(workspace.resolve("logs")).doesNotExist();
    }

    @Test
    void chatWritesWorkspaceLogFile() {
        TestApplication.service = mock(AgentService.class);
        when(TestApplication.service.process(TestApplication.session, "问题")).thenReturn("回答");
        var output = new StringWriter();
        var runtime = new BootCliRuntime(TestApplication.class);

        runtime.chat(workspace, "weather", "alice", new StringReader("问题\n/quit\n"),
                new PrintWriter(output, true));

        assertThat(output.toString()).contains("回答");
        Path logFile = workspace.resolve("logs/oryxos.log");
        assertThat(logFile).exists();
        assertThat(logFile).content().contains("INFO");
    }

    @SpringBootConfiguration
    static class ProviderQueryApplication {
    }

    @SpringBootConfiguration
    static class TestApplication {
        static AgentService service = mock(AgentService.class);

        static final Session session = new Session("cli", "alice", "weather");

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

                public Session getOrCreate(String channel, String userId, String profileName) {
                    return session;
                }

                public Optional<io.oryxos.core.session.SessionDetails> details(String id) {
                    return Optional.empty();
                }

                public boolean archive(String id) { return false; }

                public boolean restore(String id) { return false; }

                public boolean rename(String id, String title) { return false; }

                public List<io.oryxos.core.session.SessionSummary> list() { return List.of(); }
            };
        }
    }
}
