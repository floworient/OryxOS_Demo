package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.oryxos.core.profile.AgentLoader;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.prompt.Prompt;

class Lesson16QuickstartTest {

    @TempDir
    Path temp;

    @Test
    void configurationAgentLoadingAndSingleCallCloseWithoutNetwork() throws Exception {
        var started = System.nanoTime();
        var agent = temp.resolve("agents/weather/AGENT.md");
        Files.createDirectories(agent.getParent());
        Files.writeString(agent, """
                ---
                name: weather
                description: 天气助手
                identity:
                  agent_name: 天气助手
                  prompt: 提供天气信息
                provider:
                  name: qwen
                  model: qwen-plus
                ---
                请回答天气问题。
                """);
        var properties = new ProviderProperties(List.of(new ProviderDefinition(
                "qwen", ProviderDefinition.Type.DASHSCOPE, "qwen-plus", null, "test-key")));
        var profiles = new ProfileRegistry();
        new AgentLoader().loadAll(temp).forEach(profiles::register);
        var profile = profiles.find("weather").orElseThrow();
        var model = StubChatModel.text("晴", "qwen-plus");
        var registry = new ProviderRegistry(properties.providers(), Map.of("qwen", model));

        var response = new DefaultProviderService(registry, record -> record)
                .chat(null, profile, new Prompt("杭州天气"));

        assertThat(response.content()).isEqualTo("晴");
        assertThat((System.nanoTime() - started) / 1_000_000_000.0).isLessThan(300.0);
    }
}
