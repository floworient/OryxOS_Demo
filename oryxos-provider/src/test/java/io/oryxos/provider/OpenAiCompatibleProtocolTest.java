package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.provider.openai.OpenAiCompatibleChatModelFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class OpenAiCompatibleProtocolTest {

    @Test
    void sendsOpenAiCompatiblePathAuthorizationModelAndMessages() throws Exception {
        var path = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = """
                    {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"demo-model",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var definition = new ProviderDefinition("local", ProviderDefinition.Type.OPENAI_COMPATIBLE,
                    "demo-model", "http://127.0.0.1:" + server.getAddress().getPort(), "sentinel-key");
            var model = new OpenAiCompatibleChatModelFactory().create(definition);

            var response = model.call(new Prompt("hello"));

            assertThat(response.getResult().getOutput().getText()).isEqualTo("ok");
            assertThat(path).hasValue("/v1/chat/completions");
            assertThat(authorization).hasValue("Bearer sentinel-key");
            assertThat(body.get()).contains("\"model\":\"demo-model\"", "\"messages\"");
        }
        finally {
            server.stop(0);
        }
    }
}
