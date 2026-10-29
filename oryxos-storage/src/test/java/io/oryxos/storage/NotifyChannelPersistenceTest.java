package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.core.notify.NotifyChannelConflictException;
import io.oryxos.core.notify.NotifyChannelRegistry;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class NotifyChannelPersistenceTest {

    @TempDir
    Path temporary;

    @Test
    void channelsMigratePersistInStableOrderAndSurviveRestart() {
        String url = "jdbc:sqlite:" + temporary.resolve("notify.db");
        Instant first = Instant.parse("2026-10-28T08:00:00Z");
        Instant later = first.plusSeconds(60);

        try (var context = application(url).run()) {
            NotifyChannelRegistry registry = context.getBean(NotifyChannelRegistry.class);
            registry.create(channel("zeta", first, Map.of("format", "plain")));
            registry.create(channel("alpha", first, Map.of("token_hint", "four")));
            registry.create(channel("later", later, Map.of()));

            assertThat(context.getBean(JdbcTemplate.class)
                    .queryForObject("select count(*) from notify_channels", Integer.class)).isEqualTo(3);
            assertThat(registry.list()).extracting(NotifyChannel::name)
                    .containsExactly("alpha", "zeta", "later");
        }

        try (var restarted = application(url).run()) {
            NotifyChannelRegistry registry = restarted.getBean(NotifyChannelRegistry.class);
            NotifyChannel restored = registry.find("alpha").orElseThrow();
            assertThat(restored.config()).containsExactly(Map.entry("token_hint", "four"));
            assertThat(restored.createdAt()).isEqualTo(first);
        }
    }

    @Test
    void createConflictsAndUpdatePreservesCreationTime() {
        String url = "jdbc:sqlite:" + temporary.resolve("conflict.db");
        Instant first = Instant.parse("2026-10-28T08:00:00Z");
        try (var context = application(url).run()) {
            NotifyChannelRegistry registry = context.getBean(NotifyChannelRegistry.class);
            registry.create(channel("team", first, Map.of()));

            assertThatThrownBy(() -> registry.create(channel("team", first.plusSeconds(1), Map.of())))
                    .isInstanceOf(NotifyChannelConflictException.class);

            registry.update(new NotifyChannel("team", "webhook", "${NEW_URL}", "updated",
                    Map.of("format", "plain"), first, first.plusSeconds(30)));
            NotifyChannel updated = registry.find("team").orElseThrow();
            assertThat(updated.createdAt()).isEqualTo(first);
            assertThat(updated.updatedAt()).isEqualTo(first.plusSeconds(30));
            assertThat(updated.url()).isEqualTo("${NEW_URL}");

            registry.delete("team");
            assertThat(registry.find("team")).isEmpty();
        }
    }

    private static NotifyChannel channel(String name, Instant createdAt, Map<String, String> config) {
        return new NotifyChannel(name, "webhook", "${" + name.toUpperCase() + "_URL}",
                name + " description", config, createdAt, createdAt);
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
    static class TestApplication {
    }
}
