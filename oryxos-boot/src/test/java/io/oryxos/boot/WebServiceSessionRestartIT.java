package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.core.profile.AgentLoader;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.provider.DefaultProviderService;
import io.oryxos.provider.ProviderDefinition;
import io.oryxos.provider.ProviderRegistry;
import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.web.api.GlobalExceptionHandler;
import io.oryxos.web.config.WebExecutionConfiguration;
import io.oryxos.web.session.SessionApiController;
import io.oryxos.web.session.SessionWebService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
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

class WebServiceSessionRestartIT {

    @TempDir
    Path temporary;

    @Test
    void conversationArchiveAndGenerationSurviveRestartThroughRealAgentService() throws Exception {
        prepareAgent();
        String databaseUrl = "jdbc:sqlite:" + temporary.resolve("web-session.db");
        String archivedId;

        try (var context = runApplication(databaseUrl)) {
            String base = baseUrl((WebServerApplicationContext) context);
            var rest = new TestRestTemplate();
            var mapper = context.getBean(ObjectMapper.class);

            var created = rest.postForEntity(base, json("""
                    {"profileName":"daily-tech","userId":"erp-user-42"}
                    """), String.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            archivedId = data(mapper, created.getBody()).path("id").asText();
            assertThat(archivedId).isNotBlank();

            assertReply(rest, mapper, base, archivedId, "第一问", "reply-1");
            assertReply(rest, mapper, base, archivedId, "第二问", "reply-2");

            var active = rest.getForEntity(base + "/" + archivedId, String.class);
            assertThat(active.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(mapper, active.getBody()).path("messages")).hasSize(4);

            var archived = rest.exchange(base + "/" + archivedId,
                    org.springframework.http.HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
            assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(mapper, archived.getBody()).path("status").asText())
                    .isEqualTo("ARCHIVED");

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                    "select count(*) from llm_calls where session_id=?", Integer.class, archivedId))
                    .isEqualTo(2);
        }

        try (var context = runApplication(databaseUrl)) {
            String base = baseUrl((WebServerApplicationContext) context);
            var rest = new TestRestTemplate();
            var mapper = context.getBean(ObjectMapper.class);

            var restored = rest.getForEntity(base + "/" + archivedId, String.class);
            assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode restoredData = data(mapper, restored.getBody());
            assertThat(restoredData.path("status").asText()).isEqualTo("ARCHIVED");
            assertThat(restoredData.path("messages")).hasSize(4);

            var replacement = rest.postForEntity(base, json("""
                    {"profileName":"daily-tech","userId":"erp-user-42"}
                    """), String.class);
            assertThat(replacement.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(data(mapper, replacement.getBody()).path("id").asText())
                    .isNotEqualTo(archivedId);

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject(
                    "select count(*) from sessions where user_id=?", Integer.class, "erp-user-42"))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "select count(*) from llm_calls where session_id=?", Integer.class, archivedId))
                    .isEqualTo(2);
        }
    }

    private void prepareAgent() throws Exception {
        Path agent = Files.createDirectories(temporary.resolve("agents/daily-tech"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: daily-tech
                description: 科技日报
                identity: {agent_name: 日报助手, prompt: 生成简短回复}
                provider: {name: stub, model: stub-model}
                ---
                直接回答用户问题。
                """);
    }

    private org.springframework.context.ConfigurableApplicationContext runApplication(String databaseUrl) {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run(
                "--server.port=0",
                "--oryxos.workspace=" + temporary,
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.driver-class-name=org.sqlite.JDBC",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
                "--spring.flyway.locations=classpath:db/migration/sqlite");
    }

    private static void assertReply(TestRestTemplate rest, ObjectMapper mapper, String base,
            String sessionId, String message, String reply) throws Exception {
        var response = rest.postForEntity(base + "/" + sessionId + "/messages",
                json("{\"message\":\"" + message + "\"}"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(mapper, response.getBody()).path("reply").asText()).isEqualTo(reply);
    }

    private static JsonNode data(ObjectMapper mapper, String body) throws Exception {
        return mapper.readTree(body).path("data");
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static String baseUrl(WebServerApplicationContext context) {
        return "http://127.0.0.1:" + context.getWebServer().getPort() + "/api/v1/sessions";
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, ToolConfiguration.class, MemoryConfiguration.class,
            ReActConfiguration.class, WebExecutionConfiguration.class,
            SessionWebService.class, SessionApiController.class, GlobalExceptionHandler.class})
    static class TestApplication {

        @Bean
        ProfileRegistry profiles(@Value("${oryxos.workspace}") String workspace) {
            var profiles = new ProfileRegistry();
            new AgentLoader().loadAll(Path.of(workspace)).forEach(profiles::register);
            return profiles;
        }

        @Bean
        AtomicInteger providerCalls() {
            return new AtomicInteger();
        }

        @Bean
        ProviderService provider(LlmCallRecorder audit, AtomicInteger providerCalls) {
            ChatModel model = prompt -> new ChatResponse(List.of(new Generation(
                    new AssistantMessage("reply-" + providerCalls.incrementAndGet()))));
            var definition = new ProviderDefinition("stub", ProviderDefinition.Type.DASHSCOPE,
                    "stub-model", null, "not-a-real-key");
            return new DefaultProviderService(
                    new ProviderRegistry(List.of(definition), Map.of("stub", model)), audit);
        }
    }
}
