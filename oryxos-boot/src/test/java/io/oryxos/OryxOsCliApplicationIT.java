package io.oryxos;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.boot.BootCliRuntime;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OryxOsCliApplicationIT {
    @TempDir
    Path userDirectory;

    @Test
    void helpReturnsWithoutStartingSpringOrCreatingWorkspace() {
        var properties = new Properties();
        properties.setProperty("oryxos.root", "light-root");
        var output = new StringWriter();
        var error = new StringWriter();
        try (var runtime = new BootCliRuntime(OryxOsApplication.class, ignored -> { })) {
            int exit = OryxOsApplication.execute(new String[] {"--help"}, runtime,
                    new StringReader(""), new PrintWriter(output, true),
                    new PrintWriter(error, true), properties, Map.of(), userDirectory, () -> "alice");

            assertThat(exit).isZero();
            assertThat(output.toString()).contains("Usage: oryxos");
            assertThat(error.toString()).isEmpty();
            assertThat(Files.exists(userDirectory.resolve("light-root"))).isFalse();
        }
    }

    @Test
    void serveEntryStartsExistingWebOnExplicitPort() throws Exception {
        int port = availablePort();
        var properties = new Properties();
        properties.setProperty("oryxos.root", userDirectory.resolve("serve-root").toString());
        try (var runtime = new BootCliRuntime(OryxOsApplication.class, ignored -> { })) {
            int exit = OryxOsApplication.execute(
                    new String[] {"serve", "--port", Integer.toString(port)}, runtime,
                    new StringReader(""), new PrintWriter(new StringWriter(), true),
                    new PrintWriter(new StringWriter(), true), properties, Map.of(),
                    userDirectory, () -> "alice");

            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(exit).isZero();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
        }
    }

    private static int availablePort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
