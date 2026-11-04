package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.tool.ToolRegistry;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ToolConfigurationTest {

    @TempDir
    Path workspace;

    @Test
    void bindsExtraRootsWithDefaultDenyAndDefensiveSnapshot() throws Exception {
        Path data = Files.createDirectories(workspace.resolve("business"));
        new ApplicationContextRunner().withUserConfiguration(ToolConfiguration.class)
                .withPropertyValues("oryxos.workspace=" + workspace,
                        "oryxos.file.allowed-paths[0]=" + data)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(io.oryxos.tool.sandbox.Sandbox.class);
                    assertThat(context.getBean(FileSandboxProperties.class).getAllowedPaths())
                            .containsExactly(data.toString());
                });
        var properties = new FileSandboxProperties();
        assertThat(properties.getAllowedPaths()).isEmpty();
        properties.setAllowedPaths(null);
        assertThat(properties.getAllowedPaths()).isEmpty();
        var input = new java.util.ArrayList<>(List.of(data.toString()));
        properties.setAllowedPaths(input);
        input.clear();
        assertThat(properties.getAllowedPaths()).containsExactly(data.toString());
    }

    @Test
    void invalidFileRootsFailStartupWithSafeReason() {
        for (String invalid : List.of("relative-canary", workspace.resolve("missing-canary").toString(), " ")) {
            new ApplicationContextRunner().withUserConfiguration(ToolConfiguration.class)
                    .withPropertyValues("oryxos.workspace=" + workspace, "oryxos.file.allowed-paths[0]=" + invalid)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("文件白名单配置非法")
                                .hasStackTraceContaining("IllegalArgumentException");
                        assertThat(context.getStartupFailure().getMessage()).doesNotContain("canary");
                    });
        }
    }

    @Test
    void registersElevenBuiltinsAndAnnotatedJavaToolsIntoOneRegistry() {
        new ApplicationContextRunner()
                .withUserConfiguration(ToolConfiguration.class, AnnotatedTools.class)
                .withPropertyValues("oryxos.workspace=" + workspace)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ToolRegistry registry = context.getBean(ToolRegistry.class);
                    assertThat(registry.registrations()).extracting(registration -> registration.name())
                            .containsExactlyInAnyOrder(
                                    "read_file", "write_file", "list_dir", "edit_file", "grep", "glob",
                                    "shell", "http_get", "http_post", "ask_user", "web_search", "java_echo");
                    assertThat(registry.find("java_echo").orElseThrow().source().name()).isEqualTo("JAVA");
                    assertThat(context.getBean(io.oryxos.tool.mcp.McpClientService.class)).isNotNull();
                });
    }

    @Test
    void bindsHttpAndShellSandboxPropertiesWithoutImplicitDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(ToolConfiguration.class)
                .withPropertyValues(
                        "oryxos.workspace=" + workspace,
                        "oryxos.http.allowed-domains[0]=example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HttpSandboxProperties.class).getAllowedDomains())
                            .isEqualTo(Set.of("example.com"));
                    assertThat(context.getBean(ToolProperties.class).getAllowedCommands()).isEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedTools {
        @Bean
        EchoOperations echoOperations() {
            return new EchoOperations();
        }
    }

    static final class EchoOperations {
        @Tool(name = "java_echo", description = "回显")
        public String echo(String value) {
            return value;
        }
    }
}
