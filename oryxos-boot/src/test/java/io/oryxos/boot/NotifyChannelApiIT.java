package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.web.api.GlobalExceptionHandler;
import io.oryxos.web.notify.NotifyChannelApiController;
import io.oryxos.web.notify.NotifyChannelMasker;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class NotifyChannelApiIT {

    @TempDir
    Path temporary;

    @Test
    void crudUsesRealSqliteAndSurvivesApplicationRestart() {
        String databaseUrl = "jdbc:sqlite:" + temporary.resolve("api.db");
        try (var context = runApplication(databaseUrl)) {
            String baseUrl = baseUrl((WebServerApplicationContext) context);
            var rest = new TestRestTemplate();
            var created = rest.postForEntity(baseUrl, json("""
                    {"name":"team","type":"webhook","url":"https://example.com/private-1234",
                     "description":"初始描述","config":{"token":"abcdef"}}
                    """), String.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(created.getBody()).contains("\"url\":\"****1234\"")
                    .doesNotContain("private-1234", "abcdef");

            var updated = rest.exchange(baseUrl + "/team", HttpMethod.PUT, json("""
                    {"type":"webhook","url":"","description":"更新描述","config":null}
                    """), String.class);
            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody()).contains("更新描述", "****1234")
                    .doesNotContain("private-1234");
        }

        try (var restarted = runApplication(databaseUrl)) {
            String channelUrl = baseUrl((WebServerApplicationContext) restarted) + "/team";
            var rest = new TestRestTemplate();
            var restored = rest.getForEntity(channelUrl, String.class);
            assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(restored.getBody()).contains("更新描述", "****1234")
                    .doesNotContain("private-1234", "abcdef");

            assertThat(rest.exchange(channelUrl, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class)
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(rest.getForEntity(channelUrl, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static String baseUrl(WebServerApplicationContext context) {
        return "http://127.0.0.1:" + context.getWebServer().getPort() + "/api/v1/notify-channels";
    }

    private static org.springframework.context.ConfigurableApplicationContext runApplication(
            String databaseUrl) {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run(
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.driver-class-name=org.sqlite.JDBC",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
                "--spring.flyway.locations=classpath:db/migration/sqlite");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, NotifyConfiguration.class, NotifyChannelApiController.class,
            NotifyChannelMasker.class, GlobalExceptionHandler.class})
    static class TestApplication {
    }
}
