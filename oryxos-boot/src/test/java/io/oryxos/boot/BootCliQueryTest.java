package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.session.SessionSummary;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

class BootCliQueryTest {
    @TempDir
    Path workspace;

    @Test
    void providersBindWithoutResolvingMissingCredentialsAndNeverExposeThem() throws Exception {
        Files.writeString(workspace.resolve("application.yaml"), """
                oryxos:
                  providers:
                    - name: zeta
                      type: DASHSCOPE
                      model: qwen-plus
                      base-url: https://dashscope.example.test
                      api-key: ${UNSET_PROVIDER_ONE:}
                    - name: alpha
                      type: OPENAI_COMPATIBLE
                      model: deepseek-chat
                      base-url: https://openai.example.test/v1
                      api-key: ${UNSET_PROVIDER_TWO:}
                """);
        var runtime = new BootCliRuntime(ToolApplication.class, ignored -> { });

        var providers = runtime.providers(workspace);

        assertThat(providers).extracting(info -> info.name()).containsExactly("alpha", "zeta");
        assertThat(providers.get(0).type()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(providers.get(0).model()).isEqualTo("deepseek-chat");
        assertThat(providers.get(0).baseUrl()).isEqualTo("https://openai.example.test/v1");
        assertThat(providers.toString()).doesNotContain(
                "apiKey", "UNSET_PROVIDER_ONE", "UNSET_PROVIDER_TWO");
    }

    @Test
    void toolsComeFromRuntimeToolsAndEmptyRegistryStaysEmpty() {
        var populated = new BootCliRuntime(ToolApplication.class, ignored -> { });
        var empty = new BootCliRuntime(EmptyToolApplication.class, ignored -> { });

        assertThat(populated.tools(workspace)).singleElement().satisfies(info -> {
            assertThat(info.name()).isEqualTo("天气");
            assertThat(info.description()).isEqualTo("查询实时天气");
        });
        assertThat(empty.tools(workspace)).isEmpty();
    }

    @Test
    void sessionsComeFromSessionManagerSummaryOnly() {
        var runtime = new BootCliRuntime(SessionApplication.class, ignored -> { });

        assertThat(runtime.sessions(workspace)).singleElement().satisfies(info -> {
            assertThat(info.id()).isEqualTo("session-1");
            assertThat(info.profileName()).isEqualTo("digest");
            assertThat(info.status()).isEqualTo("archived");
            assertThat(info.lastActiveAt()).isEqualTo(Instant.parse("2026-10-28T08:00:00Z"));
        });
    }

    @SpringBootConfiguration
    static class ToolApplication {
        @Bean("runtimeTools")
        Map<String, OryxTool> runtimeTools() {
            OryxTool tool = new OryxTool() {
                public String getName() { return "天气"; }

                public String getDescription() { return "查询实时天气"; }

                public String getInputSchema() { return "{}"; }

                public ToolResult execute(String input) { return new ToolResult(true, "晴", null, false); }
            };
            return Map.of(tool.getName(), tool);
        }
    }

    @SpringBootConfiguration
    static class EmptyToolApplication {
        @Bean("runtimeTools")
        Map<String, OryxTool> runtimeTools() {
            return Map.of();
        }
    }

    @SpringBootConfiguration
    static class SessionApplication {
        @Bean
        SessionManager sessionManager() {
            return new SessionManager() {
                public Session getOrCreate(String channel, String userId, String profileName) {
                    return new Session(channel, userId, profileName);
                }

                public void save(Session session) { }

                public Optional<Session> find(String id) { return Optional.empty(); }

                public List<SessionSummary> list() {
                    return List.of(new SessionSummary("session-1", "digest", "archived",
                            Instant.parse("2026-10-28T08:00:00Z")));
                }
            };
        }
    }
}
