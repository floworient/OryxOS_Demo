package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oryxos.OryxOsApplication;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ToolCallIntent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class MockAgentE2ETest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    @Test
    void httpFlowReconcilesSessionMemoryToolAndLlmAuditWithoutCredential() throws Exception {
        prepareAgent();
        var application = new SpringApplication(OryxOsApplication.class, TestPolicy.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        try (var context = application.run(
                "--server.port=0",
                "--oryxos.workspace=" + temporary,
                "--spring.datasource.url=jdbc:sqlite:" + temporary.resolve("mock-e2e.db"),
                "--oryxos.providers[0].name=mock",
                "--oryxos.providers[0].type=MOCK",
                "--oryxos.providers[0].model=mock-memory")) {
            String base = "http://127.0.0.1:"
                    + ((WebServerApplicationContext) context).getWebServer().getPort();
            var rest = new TestRestTemplate();
            var created = rest.postForEntity(base + "/api/v1/sessions",
                    json("{\"profileName\":\"memory-agent\",\"userId\":\"e2e-user\"}"),
                    String.class);
            String id = JSON.readTree(created.getBody()).path("data").path("id").asText();

            var reply = rest.postForEntity(base + "/api/v1/sessions/" + id + "/messages",
                    json("{\"message\":\"记住：我喜欢喝美式咖啡\"}"), String.class);
            var list = rest.getForEntity(base + "/api/v1/sessions?status=active", String.class);
            var details = rest.getForEntity(base + "/api/v1/sessions/" + id, String.class);

            assertThat(created.getStatusCode().value()).isEqualTo(201);
            assertThat(reply.getBody()).contains("已记住这条信息");
            assertThat(list.getBody()).contains(id, "\"messageCount\":4")
                    .doesNotContain("messages");
            assertThat(details.getBody()).contains(id, "我喜欢喝美式咖啡", "save_memory");
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select count(*) from llm_calls", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from tool_invocations", Integer.class)).isEqualTo(1);
            assertThat(Files.readString(temporary.resolve("agents/memory-agent/MEMORY.md")))
                    .contains("我喜欢喝美式咖啡");
        }
    }

    private static HttpEntity<String> json(String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private void prepareAgent() throws Exception {
        Path agent = Files.createDirectories(temporary.resolve("agents/memory-agent"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: memory-agent
                description: Mock HTTP 主流程验证
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
