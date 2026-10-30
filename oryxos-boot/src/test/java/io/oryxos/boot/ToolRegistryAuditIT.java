package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class ToolRegistryAuditIT {

    @TempDir
    Path workspace;

    @AfterEach
    void clearProfile() {
        ProfileContext.clear();
    }

    @Test
    void bootRegistryExecutesVisibleBuiltinExactlyOnceAndPersistsAudit() throws Exception {
        Path agent = Files.createDirectories(workspace.resolve("agents/reader"));
        Path source = agent.resolve("AGENT.md");
        Files.writeString(source, "---\nname: reader\n---\n读取契约");
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "oryxos.workspace", workspace.toString(),
                "spring.datasource.url", "jdbc:sqlite:" + workspace.resolve("audit.db"),
                "spring.datasource.driver-class-name", "org.sqlite.JDBC",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));

        try (var context = application.run()) {
            var profile = new Profile("reader", "读取", new Profile.Identity("助手", "读取"),
                    new Profile.ProviderRef("stub", "stub", null), List.of("read_file"), List.of(),
                    List.of(), List.of(), List.of(), new Profile.Settings(3, 10), source);
            ProfileContext.set(profile);

            var result = context.getBean(ToolExecutor.class).execute("session-read",
                    new ToolCallIntent("call-read", "read_file", "{\"path\":\"AGENT.md\"}"));

            assertThat(result.success()).isTrue();
            assertThat(result.content()).contains("读取契约");
            assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                    "select count(*) from tool_invocations where session_id='session-read' "
                            + "and tool_name='read_file' and success=1", Integer.class)).isEqualTo(1);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, ToolConfiguration.class, ReActConfiguration.class})
    static class TestApplication {
        @Bean
        ProfileRegistry profiles() {
            return new ProfileRegistry();
        }

        @Bean
        ProviderService provider() {
            return (sessionId, profile, prompt) -> null;
        }

        @Bean
        BiConsumer<Profile, ToolCallIntent> allowTools() {
            return (profile, call) -> { };
        }
    }
}
