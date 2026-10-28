package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.session.SessionManager;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class SessionGetOrCreateTest {
    @TempDir
    Path temporary;

    @Test
    void stableIdentityRestoresHistoryAcrossRestartAndSeparatesEachIdentityPart() {
        String url = "jdbc:sqlite:" + temporary.resolve("sessions.db");
        String id;
        try (var first = application(url).run()) {
            var sessions = first.getBean(SessionManager.class);
            var original = sessions.getOrCreate("cli", "alice", "weather");
            original.append("第一问");
            sessions.save(original);
            id = original.id();
            assertThat(sessions.getOrCreate("cli", "alice", "weather").id()).isEqualTo(id);
            assertThat(sessions.getOrCreate("web", "alice", "weather").id()).isNotEqualTo(id);
            assertThat(sessions.getOrCreate("cli", "bob", "weather").id()).isNotEqualTo(id);
            assertThat(sessions.getOrCreate("cli", "alice", "other").id()).isNotEqualTo(id);
        }
        try (var second = application(url).run()) {
            var sessions = second.getBean(SessionManager.class);
            var restored = sessions.getOrCreate("cli", "alice", "weather");
            assertThat(restored.id()).isEqualTo(id);
            assertThat(restored.messages()).singleElement().satisfies(message ->
                    assertThat(message.getText()).isEqualTo("第一问"));
            sessions.save(restored);
            sessions.save(sessions.getOrCreate("cli", "alice", "weather"));
            assertThat(second.getBean(JdbcTemplate.class)
                    .queryForObject("select count(*) from sessions", Integer.class)).isEqualTo(1);
        }
    }

    private static SpringApplication application(String url) {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.datasource.url", url,
                "spring.datasource.driver-class-name", "org.sqlite.JDBC",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));
        return application;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(StorageConfiguration.class)
    static class TestApplication { }
}
