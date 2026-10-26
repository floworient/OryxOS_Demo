package io.oryxos.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.provider.DefaultProviderService;
import io.oryxos.provider.ProviderDefinition;
import io.oryxos.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class OpenAiTimeoutTest {

    @Test
    void shortTestTimeoutMapsRealReadTimeout() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(250);
                exchange.sendResponseHeaders(500, -1);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var definition = new ProviderDefinition("local", ProviderDefinition.Type.OPENAI_COMPATIBLE,
                    "m", "http://127.0.0.1:" + server.getAddress().getPort(), "key");
            var model = new OpenAiCompatibleChatModelFactory().create(definition, Duration.ofMillis(50));
            var service = new DefaultProviderService(new ProviderRegistry(List.of(definition),
                    Map.of("local", model)), record -> record);

            assertThatThrownBy(() -> service.chat(null, profile(), new Prompt("x")))
                    .isInstanceOfSatisfying(ProviderException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(ProviderErrorCode.TIMEOUT));
        }
        finally {
            server.stop(0);
        }
    }

    private static Profile profile() {
        return new Profile("agent", "test", new Profile.Identity("agent", "prompt"),
                new Profile.ProviderRef("local", "m", null), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(10, 20), Path.of("agent/AGENT.md"));
    }
}
