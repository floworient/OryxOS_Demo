package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.OryxOsApplication;
import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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

    @Test
    void chatUsesMockProviderThroughTheFormalRuntimeAndPersistsExactReconciliation() throws Exception {
        prepareMockWorkspace();
        var output = new StringWriter();
        var runtime = new BootCliRuntime(MockCliApplication.class);

        runtime.chat(workspace, "memory-agent", "cli-user",
                new StringReader("记住：我喜欢喝美式咖啡\n/quit\n"),
                new PrintWriter(output, true));

        assertThat(output.toString()).contains("已记住这条信息。");
        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + workspace.resolve("oryxos.db"));
                var statement = connection.createStatement()) {
            assertThat(singleInt(statement, "select count(*) from llm_calls")).isEqualTo(2);
            assertThat(singleInt(statement, "select count(*) from tool_invocations")).isEqualTo(1);
            assertThat(singleInt(statement,
                    "select message_count from sessions where channel='cli' and user_id='cli-user'"))
                    .isEqualTo(4);
        }
    }

    private void prepareMockWorkspace() throws Exception {
        Path agent = Files.createDirectories(workspace.resolve("agents/memory-agent"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: memory-agent
                description: Mock CLI 主流程验证
                identity: {agent_name: 记忆助手, prompt: 保存用户明确要求记住的事实}
                provider: {name: mock, model: mock-memory}
                tools: [save_memory]
                settings: {max_iterations: 3, max_history_turns: 10}
                ---
                使用 save_memory 保存用户事实。
                """);
        Files.writeString(agent.resolve("MEMORY.md"),
                "# MEMORY\n\n## 核心记忆\n\n## 归档记忆\n");
        Files.writeString(workspace.resolve("application.yaml"), """
                oryxos:
                  providers:
                    - name: mock
                      type: MOCK
                      model: mock-memory
                """);
    }

    private static int singleInt(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            return result.getInt(1);
        }
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

                public Optional<io.oryxos.core.session.SessionDetails> details(String id) {
                    return Optional.empty();
                }

                public boolean archive(String id) { return false; }

                public boolean restore(String id) { return false; }

                public boolean rename(String id, String title) { return false; }

                public java.util.List<io.oryxos.core.session.SessionSummary> list() { return java.util.List.of(); }
            };
        }

        @Bean(destroyMethod = "run")
        Runnable closeProbe() {
            return () -> closed.set(true);
        }
    }

    @SpringBootConfiguration
    @Import({OryxOsApplication.class, MockCliPolicy.class})
    static class MockCliApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class MockCliPolicy {
        @Bean
        BiConsumer<Profile, ToolCallIntent> allowMemoryTool() {
            return (profile, call) -> {
                if (!"save_memory".equals(call.name())) {
                    throw new SecurityException("测试仅允许 save_memory");
                }
            };
        }
    }
}
