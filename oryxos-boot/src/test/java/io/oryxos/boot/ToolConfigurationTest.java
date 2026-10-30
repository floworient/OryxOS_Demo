package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.tool.ToolRegistry;
import java.nio.file.Path;
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
