package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.tool.builtin.NotifyTools;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class NotifyToolAuditIT {

    @TempDir
    Path temporary;

    @Test
    void sendsExactlyOnceThroughToolExecutorAndPersistsOneAudit() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            var application = new SpringApplication(TestApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            try (var context = application.run(
                    "--spring.datasource.url=jdbc:sqlite:" + temporary.resolve("audit.db"),
                    "--spring.jpa.hibernate.ddl-auto=validate",
                    "--spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
                    "--spring.flyway.locations=classpath:db/migration/sqlite",
                    "--oryxos.http.allowed-domains[0]=127.0.0.1")) {
                NotifyChannelService channels = context.getBean(NotifyChannelService.class);
                channels.create(new NotifyChannelService.CreateCommand("team", "webhook",
                        "http://127.0.0.1:" + port + "/hook", null, Map.of()));
                channels.create(new NotifyChannelService.CreateCommand("blocked", "webhook",
                        "https://example.com/hook", null, Map.of()));
                channels.create(new NotifyChannelService.CreateCommand("failing", "webhook",
                        "http://127.0.0.1:" + port + "/fail", null, Map.of()));
                OryxTool notify = context.getBean(NotifyTools.class);
                try (var executor = new ToolExecutor(Map.of("notify", notify), (profile, call) -> { },
                        context.getBean(ToolInvocationRecorder.class))) {
                    assertThat(execute(executor, "session-notify", "call-notify",
                            "{\"content\":\"hello\",\"channel\":\"team\"}").success()).isTrue();
                    assertThat(execute(executor, "session-blocked", "call-blocked",
                            "{\"content\":\"hello\",\"channel\":\"blocked\"}").success()).isFalse();
                    assertThat(requests).hasValue(1);
                    assertThat(execute(executor, "session-failing", "call-failing",
                            "{\"content\":\"hello\",\"channel\":\"failing\"}").success()).isFalse();
                }
                assertThat(requests).hasValue(2);
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject(
                        "select count(*) from tool_invocations where session_id='session-notify' and success=1",
                        Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "select count(*) from tool_invocations where session_id='session-blocked' and success=0",
                        Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "select count(*) from tool_invocations where session_id='session-failing' and success=0",
                        Integer.class)).isEqualTo(1);
            }
        }
        finally {
            server.stop(0);
        }
    }

    private ToolResult execute(ToolExecutor executor, String sessionId, String callId, String arguments) {
        ProfileContext.set(profile());
        try {
            return executor.execute(sessionId, new ToolCallIntent(callId, "notify", arguments));
        }
        finally {
            ProfileContext.clear();
        }
    }

    private Profile profile() {
        return new Profile("notify-agent", "notify", new Profile.Identity("助手", "通知"),
                new Profile.ProviderRef("stub", "stub", null), List.of("notify"), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(3, 10), temporary.resolve("AGENT.md"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, NotifyConfiguration.class})
    static class TestApplication {
    }
}
