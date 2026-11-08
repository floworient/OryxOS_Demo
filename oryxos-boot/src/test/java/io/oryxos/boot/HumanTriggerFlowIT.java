package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.OryxOsApplication;
import io.oryxos.core.agent.AgentService;
import io.oryxos.core.session.SessionManager;
import io.oryxos.storage.repository.LlmCallRepository;
import io.oryxos.storage.repository.ToolInvocationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("integration")
@EnabledIfSystemProperty(named = "oryxos.human-trigger.it", matches = "true")
@EnabledIfEnvironmentVariable(named = "ALIYUN_API_KEY", matches = ".+")
@SpringBootTest(classes = OryxOsApplication.class)
class HumanTriggerFlowIT {

    private static final Path WORKSPACE = prepareWorkspace();

    @Autowired
    private AgentService agentService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private LlmCallRepository llmCalls;

    @Autowired
    private ToolInvocationRepository toolInvocations;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("oryxos.workspace", WORKSPACE::toString);
        registry.add("oryxos.providers[0].name", () -> "qwen");
        registry.add("oryxos.providers[0].type", () -> "DASHSCOPE");
        registry.add("oryxos.providers[0].model", () -> "qwen-plus");
        registry.add("oryxos.providers[0].api-key", () -> System.getenv("ALIYUN_API_KEY"));
        registry.add("oryxos.http.allowed-domains[0]", () -> "wttr.in");
    }

    @Test
    void realProviderWeatherConversationPersistsSessionAndAuditWhenExplicitlyEnabled() {
        long llmBefore = llmCalls.count();
        long toolBefore = toolInvocations.count();
        var session = sessionManager.getOrCreate("integration", "real-weather-user", "real-weather");

        String reply = agentService.process(session,
                "请按指令调用天气接口查询北京天气，并用一句中文总结。");

        assertThat(reply).isNotBlank();
        assertThat(llmCalls.count()).isGreaterThan(llmBefore);
        assertThat(toolInvocations.count()).isGreaterThan(toolBefore);
        assertThat(sessionManager.details(session.id()).orElseThrow().messages()).hasSizeGreaterThanOrEqualTo(4);
    }

    private static Path prepareWorkspace() {
        try {
            Path workspace = Files.createTempDirectory("oryxos-human-trigger-");
            Path agent = Files.createDirectories(workspace.resolve("agents/real-weather"));
            Files.writeString(agent.resolve("AGENT.md"), """
                    ---
                    name: real-weather
                    description: 真实天气人推流程验证
                    identity: {agent_name: 天气助手, prompt: 必须先调用工具获取实时天气}
                    provider: {name: qwen, model: qwen-plus}
                    tools: [http_get]
                    settings: {max_iterations: 3, max_history_turns: 10}
                    ---
                    必须调用 http_get，URL 固定为 https://wttr.in/Beijing?format=j1。
                    根据工具返回内容用一句中文总结北京天气，不得编造。
                    """);
            return workspace;
        }
        catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
