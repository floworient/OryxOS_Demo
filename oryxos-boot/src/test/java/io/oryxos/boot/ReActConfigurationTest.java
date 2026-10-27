package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.core.profile.AgentLoader;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.provider.DefaultProviderService;
import io.oryxos.provider.ProviderDefinition;
import io.oryxos.provider.ProviderRegistry;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class ReActConfigurationTest {

    @TempDir
    Path workspace;

    @Test
    void providerToolSessionAndAuditShareOneChainAndPersistAfterRestart() throws Exception {
        prepare();
        String id;
        try (var first = application(true).run()) {
            var session = new Session("cli", "alice", "weather");
            id = session.id();
            assertThat(first.getBean(AgentService.class).process(session, "天气")).isEqualTo("穿外套");
            assertThat(first.getBean(AtomicInteger.class)).hasValue(1);
            var jdbc = first.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select count(*) from llm_calls where session_id=?",
                    Integer.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from tool_invocations where session_id=?",
                    Integer.class, id)).isEqualTo(1);
            assertThat(ProfileContext.current()).isNull();
        }
        try (var second = application(false).run()) {
            assertThat(second.getBean(SessionManager.class).find(id).orElseThrow().messages()).hasSize(4);
            var session = new Session("cli", "bob", "weather");
            assertThat(second.getBean(AgentService.class).process(session, "天气")).isEqualTo("穿外套");
            assertThat(second.getBean(AtomicInteger.class)).hasValue(0);
            assertThat(second.getBean(JdbcTemplate.class).queryForObject(
                    "select success from tool_invocations where session_id=?", Integer.class, session.id())).isZero();
        }
    }

    private void prepare() throws Exception {
        Path agent = Files.createDirectories(workspace.resolve("agents/weather"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: weather
                description: 天气
                identity: {agent_name: 助手, prompt: 天气助手}
                provider: {name: stub, model: stub-model}
                tools: [lookup]
                ---
                按结果回答
                """);
    }

    private SpringApplication application(boolean allow) {
        var application = allow ? new SpringApplication(TestApplication.class, AllowTools.class)
                : new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "oryxos.workspace", workspace.toString(),
                "spring.datasource.url", "jdbc:sqlite:" + workspace.resolve("runtime.db"),
                "spring.datasource.driver-class-name", "org.sqlite.JDBC",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));
        return application;
    }

    @Configuration(proxyBeanMethods = false)
    static class AllowTools {

        @Bean
        BiConsumer<Profile, ToolCallIntent> allowTools() {
            return (profile, call) -> { };
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, ReActConfiguration.class})
    static class TestApplication {

        @Bean
        ProfileRegistry profiles(@Value("${oryxos.workspace}") String workspace) {
            var profiles = new ProfileRegistry();
            new AgentLoader().loadAll(Path.of(workspace)).forEach(profiles::register);
            return profiles;
        }

        @Bean
        AtomicInteger executions() {
            return new AtomicInteger();
        }

        @Bean
        OryxTool lookup(AtomicInteger executions) {
            return new OryxTool() {
                public String getName() {
                    return "lookup";
                }

                public String getDescription() {
                    return "查询";
                }

                public String getInputSchema() {
                    return "{\"type\":\"object\"}";
                }

                public ToolResult execute(String input) {
                    assertThat(ProfileContext.current().name()).isEqualTo("weather");
                    executions.incrementAndGet();
                    return new ToolResult(true, "晴", null, false);
                }
            };
        }

        @Bean
        ProviderService provider(LlmCallRecorder audit) {
            ChatModel model = prompt -> {
                var options = (ToolCallingChatOptions) prompt.getOptions();
                assertThat(options.getInternalToolExecutionEnabled()).isFalse();
                assertThat(options.getToolCallbacks()).hasSize(1);
                boolean observed = prompt.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance);
                AssistantMessage output = observed ? new AssistantMessage("穿外套")
                        : AssistantMessage.builder().content("").toolCalls(List.of(
                                new AssistantMessage.ToolCall("call-1", "function", "lookup", "{}"))).build();
                return new ChatResponse(List.of(new Generation(output)));
            };
            var definition = new ProviderDefinition("stub", ProviderDefinition.Type.DASHSCOPE,
                    "stub-model", null, "not-a-real-key");
            return new DefaultProviderService(new ProviderRegistry(List.of(definition), Map.of("stub", model)), audit);
        }
    }
}
