package io.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.tool.sandbox.WhitelistSandbox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpConfigLoaderTest {

    @TempDir
    Path workspace;

    @Test
    void strictlyLoadsStdioAndSseAndResolvesCompositeSecrets() throws Exception {
        Files.writeString(workspace.resolve("mcp_servers.yaml"), """
                servers:
                  - name: local
                    transport: stdio
                    command: java
                    args: ["-version"]
                    env:
                      API_TOKEN: ${TOKEN}
                  - name: remote
                    transport: sse
                    url: http://127.0.0.1:8080/sse
                    request_timeout: 12
                    headers:
                      Authorization: Bearer ${TOKEN}
                """);
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow());
        var sandbox = new WhitelistSandbox(Set.of("127.0.0.1"), Map.of("java", java),
                new SkillBindingResolver(workspace));
        var definitions = new McpConfigLoader(workspace, sandbox,
                key -> "TOKEN".equals(key) ? "canary-secret" : null).load();

        assertThat(definitions).hasSize(2);
        assertThat(definitions.get(0).command()).isEqualTo(java.toRealPath());
        assertThat(definitions.get(0).environment()).containsEntry("API_TOKEN", "canary-secret");
        assertThat(definitions.get(1).url().getHost()).isEqualTo("127.0.0.1");
        assertThat(definitions.get(1).headers()).containsEntry("Authorization", "Bearer canary-secret");
        assertThat(definitions.toString()).doesNotContain("canary-secret");
    }

    @Test
    void rejectsUnknownDuplicateAndMissingSecretWithoutLeakingValues() throws Exception {
        var sandbox = new WhitelistSandbox(Set.of("example.com"), Map.of(),
                new SkillBindingResolver(workspace));
        Files.writeString(workspace.resolve("mcp_servers.yaml"), """
                servers:
                  - name: bad
                    transport: sse
                    url: https://example.com/sse
                    headers: {Authorization: "Bearer ${MISSING}"}
                    unknown: canary-secret
                """);
        var loader = new McpConfigLoader(workspace, sandbox, ignored -> null);
        assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("canary-secret")
                .hasMessageNotContaining("MISSING")
                .hasMessageNotContaining("Authorization");

        Files.writeString(workspace.resolve("mcp_servers.yaml"), """
                servers:
                  - {name: same, transport: sse, url: https://example.com/sse}
                  - {name: same, transport: sse, url: https://example.com/sse}
                """);
        assertThatThrownBy(loader::load).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingFileAndMissingServersProduceEmptyDefinitions() throws Exception {
        var sandbox = new WhitelistSandbox(Set.of(), Map.of(), new SkillBindingResolver(workspace));
        var loader = new McpConfigLoader(workspace, sandbox, ignored -> null);
        assertThat(loader.load()).isEmpty();
        Files.writeString(workspace.resolve("mcp_servers.yaml"), "{}");
        assertThat(loader.load()).isEmpty();
    }
}
