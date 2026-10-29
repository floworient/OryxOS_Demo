package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.tool.builtin.NotifyTools;
import io.oryxos.tool.notify.NotifyAdapterRegistry;
import io.oryxos.tool.notify.NotifyChannelAdapter;
import io.oryxos.tool.sandbox.ActionType;
import io.oryxos.tool.sandbox.Sandbox;
import io.oryxos.tool.sandbox.SandboxAction;
import io.oryxos.storage.config.StorageConfiguration;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

class NotifyConfigurationTest {

    @TempDir
    Path temporary;

    @Test
    void assemblesSingleWebhookAdapterNotifyToolAndSafeDefaultSandbox() {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.datasource.url", "jdbc:sqlite:" + temporary.resolve("notify.db"),
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));
        try (var context = application.run(
                "--spring.datasource.url=jdbc:sqlite:" + temporary.resolve("notify.db"))) {
            assertThat(context.getBean(NotifyChannelService.class)).isNotNull();
            assertThat(context.getBeansOfType(NotifyChannelAdapter.class)).hasSize(1);
            assertThat(context.getBean(NotifyAdapterRegistry.class).require("webhook")).isNotNull();
            OryxTool notify = context.getBean(NotifyTools.class);
            assertThat(notify.getName()).isEqualTo("notify");
            assertThatThrownBy(() -> context.getBean(Sandbox.class).enforce(
                    new SandboxAction(ActionType.HTTP_REQUEST, URI.create("https://example.com"))))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, NotifyConfiguration.class})
    static class TestApplication {
    }
}
