package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.core.profile.AgentLoader;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.provider.DefaultProviderService;
import io.oryxos.provider.ProviderDefinition;
import io.oryxos.provider.ProviderRegistry;
import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.web.agent.AgentApiController;
import io.oryxos.web.agent.AgentInvocationWebService;
import io.oryxos.web.api.GlobalExceptionHandler;
import io.oryxos.web.config.WebExecutionConfiguration;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class WebServiceAgentIT {

    @TempDir
    Path temporary;

    @Test
    void twoInvocationsUseIndependentSessionsAndPersistLlmAndToolAudit() throws Exception {
        prepareAgent();
        try (var context = runApplication()) {
            String url = "http://127.0.0.1:"
                    + ((WebServerApplicationContext) context).getWebServer().getPort()
                    + "/api/v1/agents/report/invoke";
            var rest = new TestRestTemplate();

            var first = rest.postForEntity(url, json("{\"message\":\"生成日报\"}"), String.class);
            var second = rest.postForEntity(url, json("{\"message\":\"再次生成\"}"), String.class);

            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(first.getBody()).contains("\"reply\":\"日报完成\"")
                    .doesNotContain("sessionId");
            assertThat(second.getBody()).contains("\"reply\":\"日报完成\"")
                    .doesNotContain("sessionId");
            assertThat(context.getBean(AtomicInteger.class)).hasValue(2);

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                    "select count(*) from sessions where channel='web-invoke'", Integer.class))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from llm_calls", Integer.class))
                    .isEqualTo(4);
            assertThat(jdbc.queryForObject("select count(*) from tool_invocations", Integer.class))
                    .isEqualTo(2);
            assertThat(jdbc.queryForList("""
                    select count(*) as calls from llm_calls l
                    join sessions s on s.session_id=l.session_id
                    where s.channel='web-invoke' group by l.session_id
                    """, Integer.class)).containsExactlyInAnyOrder(2, 2);
        }
    }

    private void prepareAgent() throws Exception {
        Path agent = Files.createDirectories(temporary.resolve("agents/report"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: report
                description: 日报
                identity: {agent_name: 日报助手, prompt: 使用工具生成日报}
                provider: {name: stub, model: stub-model}
                tools: [lookup]
                ---
                必须根据工具结果回答。
                """);
    }

    private org.springframework.context.ConfigurableApplicationContext runApplication() {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run(
                "--server.port=0",
                "--oryxos.workspace=" + temporary,
                "--spring.datasource.url=jdbc:sqlite:" + temporary.resolve("agent.db"),
                "--spring.datasource.driver-class-name=org.sqlite.JDBC",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
                "--spring.flyway.locations=classpath:db/migration/sqlite");
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, ToolConfiguration.class, MemoryConfiguration.class,
            ReActConfiguration.class, WebExecutionConfiguration.class,
            AgentInvocationWebService.class, AgentApiController.class, GlobalExceptionHandler.class})
    static class TestApplication {

        @Bean
        ProfileRegistry profiles(@Value("${oryxos.workspace}") String workspace) {
            var profiles = new ProfileRegistry();
            new AgentLoader().loadAll(Path.of(workspace)).forEach(profiles::register);
            return profiles;
        }

        @Bean
        AtomicInteger toolExecutions() {
            return new AtomicInteger();
        }

        @Bean
        BiConsumer<Profile, ToolCallIntent> allowTools() {
            return (profile, call) -> { };
        }

        @Bean
        OryxTool lookup(AtomicInteger toolExecutions) {
            return new OryxTool() {
                public String getName() {
                    return "lookup";
                }

                public String getDescription() {
                    return "查询日报数据";
                }

                public String getInputSchema() {
                    return "{\"type\":\"object\"}";
                }

                public ToolResult execute(String input) {
                    toolExecutions.incrementAndGet();
                    return new ToolResult(true, "数据", null, false);
                }
            };
        }

        @Bean
        ProviderService provider(LlmCallRecorder audit) {
            ChatModel model = prompt -> {
                boolean hasToolResult = prompt.getInstructions().stream()
                        .anyMatch(ToolResponseMessage.class::isInstance);
                AssistantMessage output = hasToolResult
                        ? new AssistantMessage("日报完成")
                        : AssistantMessage.builder().content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "call-1", "function", "lookup", "{}"))).build();
                return new ChatResponse(List.of(new Generation(output)));
            };
            var definition = new ProviderDefinition("stub", ProviderDefinition.Type.DASHSCOPE,
                    "stub-model", null, "not-a-real-key");
            return new DefaultProviderService(
                    new ProviderRegistry(List.of(definition), Map.of("stub", model)), audit);
        }
    }
}
