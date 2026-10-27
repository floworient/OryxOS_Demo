package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class SessionPersistenceTest {

    @TempDir
    Path temporary;

    @Test
    void completeMessagesAndIdentityRestoreAfterRestartAndInvalidDataFails() {
        String url = "jdbc:sqlite:" + temporary.resolve("sessions.db");
        var original = new Session("cli", "alice", "weather");
        original.append("天气");
        var call = new ToolCallIntent("call-a", "lookup", "{}");
        var next = new ToolCallIntent("call-b", "lookup", "{\"city\":\"北京\"}");
        original.append(new ProviderResponse("p", "m", "", TokenUsage.UNKNOWN, List.of(call, next), 0));
        original.appendToolResult(call, new ToolResult(true, "晴", null, false));
        original.appendToolResult(next, new ToolResult(false, "", "服务不可用", false));
        original.appendAssistant("穿外套");
        try (var first = application(url).run()) {
            var sessions = first.getBean(SessionManager.class);
            sessions.save(original);
            sessions.save(original);
            assertThat(sessions.find("missing")).isEmpty();
        }
        try (var second = application(url).run()) {
            var sessions = second.getBean(SessionManager.class);
            var restored = sessions.find(original.id()).orElseThrow();
            assertThat(restored.id()).isEqualTo(original.id());
            assertThat(restored.messages()).hasSize(5);
            assertThat(restored.messages().getFirst().getText()).isEqualTo("天气");
            assertThat(restored.messages().getLast().getText()).isEqualTo("穿外套");
            var result = (ToolResponseMessage) restored.messages().get(3);
            assertThat(result.getResponses().getFirst().id()).isEqualTo("call-b");
            assertThat(result.getResponses().getFirst().responseData()).contains("服务不可用");
            var jdbc = second.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("update sessions set status='invalid'"))
                    .isInstanceOf(RuntimeException.class);
            jdbc.update("update sessions set messages_json='[] garbage'");
            assertThatThrownBy(() -> sessions.find(original.id())).isInstanceOf(IllegalStateException.class);
            jdbc.update("update sessions set messages_json='broken'");
            assertThatThrownBy(() -> sessions.find(original.id())).isInstanceOf(IllegalStateException.class);
            jdbc.update("update sessions set messages_json='[{\"role\":\"unknown\",\"text\":\"x\"}]'");
            assertThatThrownBy(() -> sessions.find(original.id())).isInstanceOf(IllegalStateException.class);
            jdbc.update("update sessions set messages_json="
                    + "'[{\"role\":\"tool\",\"responses\":[{\"id\":\"missing\","
                    + "\"name\":\"lookup\",\"responseData\":\"x\"}]}]'");
            assertThatThrownBy(() -> sessions.find(original.id())).isInstanceOf(IllegalStateException.class);
        }
    }

    private static SpringApplication application(String url) {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.datasource.url", url, "spring.datasource.driver-class-name", "org.sqlite.JDBC",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));
        return application;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(StorageConfiguration.class)
    static class TestApplication {
    }
}
