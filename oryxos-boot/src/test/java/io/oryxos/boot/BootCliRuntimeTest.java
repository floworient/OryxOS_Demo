package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.OryxOsApplication;
import io.oryxos.cli.OryxOsCli;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootCliRuntimeTest {
    @TempDir
    Path workspace;

    @Test
    void serveStartsExistingWebOnRequestedPortAndCloseReleasesContext() throws Exception {
        int port = availablePort();
        var reports = new ArrayList<String>();
        var runtime = new BootCliRuntime(OryxOsApplication.class, reports::add);
        try {
            runtime.serve(workspace, port);

            var request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build();
            var response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
            assertThat(runtime.activeContextCount()).isOne();
            assertThat(runtime.activeWorkspace()).contains(workspace.toAbsolutePath().normalize());
        }
        finally {
            runtime.close();
        }
        assertThat(runtime.activeContextCount()).isZero();
    }

    @Test
    void gatewayReportsOnlyComponentsActuallyPresentInContext() {
        String previous = System.getProperty("server.port");
        System.setProperty("server.port", "0");
        var reports = new ArrayList<String>();
        var runtime = new BootCliRuntime(OryxOsApplication.class, reports::add);
        try {
            runtime.gateway(workspace);

            assertThat(reports).anyMatch(line -> line.contains("http"));
            assertThat(reports).noneMatch(line -> line.matches("(?i).*(feishu|wecom|dingtalk).*"));
            assertThat(runtime.activeContextCount()).isOne();
        }
        finally {
            runtime.close();
            restoreSystemProperty("server.port", previous);
        }
    }

    @Test
    void occupiedPortFailsWithoutRetainingAContext() throws Exception {
        var runtime = new BootCliRuntime(OryxOsApplication.class, ignored -> { });
        try (var occupied = new ServerSocket(0)) {
            assertThatThrownBy(() -> runtime.serve(workspace, occupied.getLocalPort()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(runtime.activeContextCount()).isZero();
        }
        finally {
            runtime.close();
        }
    }

    @Test
    void commandRejectsPortsOutsideTcpRangeAsUsageErrors() {
        var runtime = new BootCliRuntime(OryxOsApplication.class, ignored -> { });
        var output = new StringWriter();
        var error = new StringWriter();
        try {
            var cli = new OryxOsCli(runtime, workspace, new StringReader(""),
                    new PrintWriter(output, true), new PrintWriter(error, true), () -> "alice");

            assertThat(cli.execute("serve", "--port", "0")).isEqualTo(2);
            assertThat(cli.execute("serve", "--port", "65536")).isEqualTo(2);
            assertThat(runtime.activeContextCount()).isZero();
        }
        finally {
            runtime.close();
        }
    }

    private static int availablePort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        }
        else {
            System.setProperty(name, value);
        }
    }
}
