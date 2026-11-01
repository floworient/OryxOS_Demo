package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.memory.MarkdownMemoryStore;
import io.oryxos.memory.mem0.Mem0MemoryStore;
import io.oryxos.storage.JpaMemoryStore;
import io.oryxos.storage.repository.MemoryEntryRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MemoryConfigurationTest {

    private static final String OPENAPI = """
            {"paths":{
              "/memories":{
                "get":{"parameters":[{"name":"agent_id","in":"query"}],
                  "responses":{"200":{"content":{"application/json":{
                    "schema":{"$ref":"#/components/schemas/Envelope"}}}}}},
                "post":{"requestBody":{"content":{"application/json":{
                  "schema":{"$ref":"#/components/schemas/Create"}}}}}},
              "/search":{"post":{
                "requestBody":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Search"}}}},
                "responses":{"200":{"content":{"application/json":{
                  "schema":{"$ref":"#/components/schemas/Envelope"}}}}}}}},
             "components":{"schemas":{
               "Create":{"properties":{"agent_id":{},"metadata":{},"infer":{}}},
               "Search":{"properties":{"query":{},"agent_id":{}}},
               "Envelope":{"properties":{"results":{"type":"array","items":{"$ref":"#/components/schemas/Memory"}}}},
               "Memory":{"properties":{"agent_id":{},"metadata":{},"memory":{}}}}}}
            """;

    @TempDir
    Path workspace;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void selectsMarkdownByDefaultWithoutContactingMem0() {
        runner().withPropertyValues(
                        "oryxos.memory.mem0.base-url=http://127.0.0.1:1",
                        "oryxos.memory.mem0.api-key=unused")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LongTermMemoryStore.class))
                            .isInstanceOf(MarkdownMemoryStore.class);
                });
    }

    @Test
    void selectsSqliteWhenRepositoryIsAvailable() {
        runner().withBean(MemoryEntryRepository.class, () -> mock(MemoryEntryRepository.class))
                .withPropertyValues("oryxos.memory.backend=sqlite")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LongTermMemoryStore.class)).isInstanceOf(JpaMemoryStore.class);
                });
    }

    @Test
    void selectsMem0AndValidatesItsReadOnlyOpenApi() throws IOException {
        startOpenApiServer();
        runner().withPropertyValues(
                        "oryxos.memory.backend=mem0",
                        "oryxos.memory.mem0.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                        "oryxos.memory.mem0.api-key=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LongTermMemoryStore.class))
                            .isInstanceOf(Mem0MemoryStore.class);
                });
    }

    @Test
    void rejectsUnknownBackend() {
        runner().withPropertyValues("oryxos.memory.backend=unknown")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveLimits() {
        runner().withPropertyValues("oryxos.memory.archival-max-characters=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingMem0Configuration() {
        runner().withPropertyValues("oryxos.memory.backend=mem0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsReservedHybridCapability() {
        LongTermMemoryStore store = mock(LongTermMemoryStore.class);
        when(store.capabilities()).thenReturn(MemoryCapabilities.HYBRID_BUILTIN);
        runner().withBean(LongTermMemoryStore.class, () -> store)
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(MemoryConfiguration.class)
                .withPropertyValues("oryxos.workspace=" + workspace);
    }

    private void startOpenApiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi.json", exchange -> {
            byte[] body = OPENAPI.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }
}
