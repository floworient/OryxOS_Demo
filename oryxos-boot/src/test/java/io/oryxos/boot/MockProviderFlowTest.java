package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.OryxOsApplication;
import io.oryxos.core.agent.AgentService;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.SessionManager;
import io.oryxos.storage.repository.LlmCallRepository;
import io.oryxos.storage.repository.ToolInvocationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MockProviderFlowTest {

    @TempDir
    Path temporary;

    @Test
    void drivesTheFormalTwoRoundMemorySessionAndAuditFlowWithoutCredential() throws Exception {
        prepareAgent();
        var application = new SpringApplication(OryxOsApplication.class, TestPolicy.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run(
                "--oryxos.workspace=" + temporary,
                "--spring.datasource.url=jdbc:sqlite:" + temporary.resolve("mock-flow.db"),
                "--oryxos.providers[0].name=mock",
                "--oryxos.providers[0].type=MOCK",
                "--oryxos.providers[0].model=mock-memory")) {
            var sessions = context.getBean(SessionManager.class);
            var session = sessions.getOrCreate("test", "user-1", "memory-agent");

            String reply;
            try {
                reply = context.getBean(AgentService.class)
                        .process(session, "记住：我喜欢喝美式咖啡");
            }
            catch (RuntimeException exception) {
                var audit = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                        .queryForList("select success, error_message, result_json from tool_invocations");
                var schema = context.getBean(io.oryxos.core.tool.ToolRegistry.class)
                        .find("save_memory").orElseThrow().tool().getInputSchema();
                var arguments = ((org.springframework.ai.chat.messages.AssistantMessage)
                        session.messages().get(1)).getToolCalls().getFirst().arguments();
                throw new AssertionError("Mock 主流程失败，工具审计=" + audit + "，arguments="
                        + arguments + "，schema=" + schema,
                        exception);
            }

            assertThat(reply).isEqualTo("已记住这条信息。");
            assertThat(sessions.details(session.id()).orElseThrow().messages()).hasSize(4);
            assertThat(context.getBean(LlmCallRepository.class).count()).isEqualTo(2);
            assertThat(context.getBean(ToolInvocationRepository.class).count()).isEqualTo(1);
            Profile profile = context.getBean(ProfileRegistry.class).find("memory-agent").orElseThrow();
            assertThat(context.getBean(MemoryService.class).loadLongTerm(profile).archival())
                    .singleElement().satisfies(entry ->
                            assertThat(entry.content()).isEqualTo("我喜欢喝美式咖啡"));
        }
    }

    private void prepareAgent() throws Exception {
        Path agent = Files.createDirectories(temporary.resolve("agents/memory-agent"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: memory-agent
                description: Mock 主流程验证
                identity: {agent_name: 记忆助手, prompt: 保存用户明确要求记住的事实}
                provider: {name: mock, model: mock-memory}
                tools: [save_memory]
                settings: {max_iterations: 3, max_history_turns: 10}
                ---
                使用 save_memory 保存用户事实。
                """);
        Files.writeString(agent.resolve("MEMORY.md"),
                "# MEMORY\n\n## 核心记忆\n\n## 归档记忆\n");
    }

    @Configuration(proxyBeanMethods = false)
    static class TestPolicy {
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
