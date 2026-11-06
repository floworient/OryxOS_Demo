package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.core.profile.AgentLoader;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskStore;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class SchedulerAgentServiceIT {

    @TempDir
    Path workspace;

    @Test
    void scheduledCallbackUsesAgentChainAndPersistsAuditExactlyOnce() throws Exception {
        prepareAgent();

        try (var context = application().run()) {
            Runnable callback = (Runnable) context.getBean(AtomicReference.class).get();
            assertThat(callback).isNotNull();

            callback.run();

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String sessionId = jdbc.queryForObject("""
                    select session_id from sessions
                    where channel='scheduler' and user_id='scheduler' and profile_name='weather'
                    """, String.class);
            assertThat(jdbc.queryForObject(
                            "select count(*) from sessions where session_id=?", Integer.class, sessionId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "select count(*) from llm_calls where session_id=?", Integer.class, sessionId))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                            "select count(*) from tool_invocations where session_id=?", Integer.class, sessionId))
                    .isEqualTo(1);
            assertThat(context.getBean(AtomicInteger.class)).hasValue(1);
            assertThat(context.getBean(ScheduledTaskStore.class)
                            .executions(ScheduleId.from("weather", "daily")))
                    .singleElement()
                    .satisfies(execution -> {
                        assertThat(execution.success()).isTrue();
                        assertThat(execution.sessionId()).isEqualTo(sessionId);
                    });
            assertThat(ProfileContext.current()).isNull();
        }
    }

    private void prepareAgent() throws Exception {
        Path agent = Files.createDirectories(workspace.resolve("agents/weather"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: weather
                description: 天气
                identity: {agent_name: 助手, prompt: 天气助手}
                provider: {name: stub, model: stub-model}
                tools: [lookup]
                schedules:
                  - key: daily
                    cron: "0 0 8 * * *"
                    zone: Asia/Shanghai
                    message: 查询天气
                ---
                按工具结果回答
                """);
    }

    private SpringApplication application() {
        SpringApplication application = new SpringApplication(TestApplication.class);
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, ToolConfiguration.class, MemoryConfiguration.class,
            ReActConfiguration.class, SchedulerConfiguration.class})
    static class TestApplication {

        @Bean
        ProfileRegistry profiles(@Value("${oryxos.workspace}") String workspace) {
            ProfileRegistry profiles = new ProfileRegistry();
            new AgentLoader().loadAll(Path.of(workspace)).forEach(profiles::register);
            return profiles;
        }

        @Bean
        AtomicInteger executions() {
            return new AtomicInteger();
        }

        @Bean
        AtomicReference<Runnable> scheduledCallback() {
            return new AtomicReference<>();
        }

        @Bean
        @Primary
        TaskScheduler capturingTaskScheduler(AtomicReference<Runnable> scheduledCallback) {
            TaskScheduler scheduler = mock(TaskScheduler.class);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            doAnswer(invocation -> {
                scheduledCallback.set(invocation.getArgument(0));
                return future;
            }).when(scheduler).schedule(any(Runnable.class), any(Trigger.class));
            return scheduler;
        }

        @Bean
        BiConsumer<Profile, ToolCallIntent> allowTools() {
            return (profile, call) -> { };
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
                ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
                assertThat(options.getInternalToolExecutionEnabled()).isFalse();
                assertThat(options.getToolCallbacks()).hasSize(1);
                boolean hasToolResult = prompt.getInstructions().stream()
                        .anyMatch(ToolResponseMessage.class::isInstance);
                AssistantMessage output = hasToolResult
                        ? new AssistantMessage("穿外套")
                        : AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "call-1", "function", "lookup", "{}")))
                                .build();
                return new ChatResponse(List.of(new Generation(output)));
            };
            ProviderDefinition definition = new ProviderDefinition(
                    "stub", ProviderDefinition.Type.DASHSCOPE,
                    "stub-model", null, "not-a-real-key");
            return new DefaultProviderService(
                    new ProviderRegistry(List.of(definition), Map.of("stub", model)), audit);
        }
    }
}
