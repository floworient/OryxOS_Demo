package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentLoaderTest {

    @TempDir
    Path workspace;

    @Test
    void loadsEveryCurrentProfileFieldAndIgnoresMarkdownBody() throws Exception {
        Path agentDir = Files.createDirectories(workspace.resolve("agents/weather"));
        Files.writeString(agentDir.resolve("AGENT.md"), """
                ---
                name: weather
                description: 每日天气助手
                identity:
                  agent_name: 天气助手
                  prompt: 提供准确天气
                provider:
                  name: qwen
                  model: qwen-plus
                  temperature: 0.2
                tools: [http_get, notify, http_get]
                mcp_servers: [weather-mcp]
                channels:
                  - name: cli
                    config: {format: text}
                schedules:
                  - key: daily-weather
                    cron: "0 0 8 * * *"
                    zone: Asia/Shanghai
                    message: 查询天气
                bootstrap: [AGENTS.md, SOUL.md]
                settings:
                  max_iterations: 12
                  max_history_turns: 30
                ---
                skills: 这里是正文，不是 YAML。
                """);

        Profile profile = new AgentLoader().loadAll(workspace).getFirst();

        assertThat(profile.name()).isEqualTo("weather");
        assertThat(profile.identity().agentName()).isEqualTo("天气助手");
        assertThat(profile.provider()).isEqualTo(new Profile.ProviderRef("qwen", "qwen-plus", 0.2));
        assertThat(profile.tools()).containsExactly("http_get", "notify");
        assertThat(profile.mcpServers()).containsExactly("weather-mcp");
        assertThat(profile.channels().getFirst().config()).containsEntry("format", "text");
        assertThat(profile.schedules().getFirst().key()).isEqualTo("daily-weather");
        assertThat(profile.bootstrap()).containsExactly("AGENTS.md", "SOUL.md");
        assertThat(profile.settings()).isEqualTo(new Profile.Settings(12, 30));
        assertThat(profile.sourcePath()).isEqualTo(agentDir.resolve("AGENT.md").toAbsolutePath().normalize());
    }

    @Test
    void missingWorkspaceReturnsEmptyResult() {
        assertThat(new AgentLoader().loadAll(workspace.resolve("missing"))).isEmpty();
    }

    @Test
    void rejectsSettingsOutsideIntegerRangeInsteadOfOverflowing() throws Exception {
        Path agentDir = Files.createDirectories(workspace.resolve("agents/overflow"));
        Files.writeString(agentDir.resolve("AGENT.md"), """
                ---
                name: overflow
                description: invalid
                identity: {agent_name: Overflow, prompt: Test}
                provider: {name: qwen, model: qwen-plus}
                settings: {max_iterations: 4294967297, max_history_turns: 20}
                ---
                body
                """);

        assertThat(new AgentLoader().loadAll(workspace)).isEmpty();
    }
}
