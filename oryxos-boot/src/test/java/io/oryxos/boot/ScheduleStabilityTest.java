package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.OryxOsApplication;
import io.oryxos.tool.notify.NotifyDeliveryException;
import io.oryxos.tool.notify.NotifyTarget;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ScheduleStabilityTest {

    @TempDir
    Path workspace;

    @Test
    void notifyStopsWaitingForSlowWebhook() throws Exception {
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/notify", exchange -> {
            try (exchange) {
                received.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        server.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var config = new NotifyConfiguration();
            var adapter = config.webhookNotifyAdapter(config.notifyRestClient());
            var send = executor.submit(() -> adapter.send(new NotifyTarget("webhook",
                    Map.of("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/notify")), "test"));
            try {
                assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> send.get(14, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(NotifyDeliveryException.class);
            } finally {
                release.countDown();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableMcpDoesNotBlockStartupOrRequireOpenAiCredential(CapturedOutput output) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(503, -1);
            }
        });
        server.start();
        try {
            Files.writeString(workspace.resolve("mcp_servers.yaml"), """
                    servers:
                      - name: unavailable
                        transport: sse
                        url: http://127.0.0.1:%d/sse
                        request_timeout: 1
                    """.formatted(server.getAddress().getPort()));
            var app = new SpringApplication(OryxOsApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            try (var context = app.run("--oryxos.workspace=" + workspace,
                    "--oryxos.http.allowed-domains[0]=127.0.0.1")) {
                assertThat(context.isActive()).isTrue();
                assertThat(output.getAll()).contains("MCP server 不可用", "serverName=unavailable");
            }
        } finally {
            server.stop(0);
        }
    }
}
