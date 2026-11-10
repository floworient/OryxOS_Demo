package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.audit.LlmCallPurpose;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.provider.dashscope.DashScopeChatModelFactory;
import io.oryxos.provider.openai.OpenAiCompatibleChatModelFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.util.json.JsonParser;

class OpenAiCompatibleProtocolTest {

    @ParameterizedTest
    @EnumSource(value = ProviderDefinition.Type.class, names = {"OPENAI_COMPATIBLE", "DASHSCOPE"})
    void preservesStableWireFieldsAndSendsSummaryWithoutTools(ProviderDefinition.Type type) throws Exception {
        boolean dash = type == ProviderDefinition.Type.DASHSCOPE;
        var captured = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String json = dash ? """
                    {"request_id":"id","output":{"choices":[{"message":{"role":"assistant","content":"ok"},
                    "finish_reason":"stop"}]},"usage":{"input_tokens":2,"output_tokens":1,"total_tokens":3}}
                    """ : """
                    {"id":"id","object":"chat.completion","created":1,"model":"main-model",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var main = new ProviderDefinition("main", type, "main-model", base, "test-key");
            var summary = new ProviderDefinition("summary", type, "summary-model", base, "test-key");
            var mainModel = dash ? new DashScopeChatModelFactory().create(main)
                    : new OpenAiCompatibleChatModelFactory().create(main);
            var summaryModel = dash ? new DashScopeChatModelFactory().create(summary)
                    : new OpenAiCompatibleChatModelFactory().create(summary);
            var calls = new ArrayList<io.oryxos.core.audit.LlmCallRecord>();
            var service = new DefaultProviderService(
                    new ProviderRegistry(List.of(main, summary), Map.of("main", mainModel, "summary", summaryModel)),
                    record -> {
                        calls.add(record);
                        return record;
                    });
            var profile = new Profile("test", "test", new Profile.Identity("test", "test"),
                    new Profile.ProviderRef("main", "main-model", null), List.of(), List.of(), List.of(), List.of(),
                    List.of(), new Profile.Settings(10, 20), Path.of("AGENT.md"));
            var tool = new ToolSchemaAdapter().adapt(new OryxTool() {
                public String getName() {
                    return "lookup";
                }

                public String getDescription() {
                    return "Lookup";
                }

                public String getInputSchema() {
                    return "{\"type\":\"object\"}";
                }

                public ToolResult execute(String input) {
                    throw new AssertionError("协议适配不得执行工具");
                }
            });
            var options = ToolCallingChatOptions.builder().toolCallbacks(List.of(tool))
                    .maxTokens(2048).internalToolExecutionEnabled(false).build();
            var mapper = JsonParser.getObjectMapper();
            service.chat("session", profile, new Prompt(List.of(new SystemMessage("fixed"),
                    new SystemMessage("memory A"), new UserMessage("time A")), options));
            var first = mapper.readTree(captured.get());
            var assistant = AssistantMessage.builder().content("").toolCalls(List.of(
                    new AssistantMessage.ToolCall("first", "function", "lookup", "{}"),
                    new AssistantMessage.ToolCall("second", "function", "lookup", "{}"))).build();
            var toolResults = List.of("first", "second").stream().map(id -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(id, "lookup", "{\"truncated\":true}")))
                    .build()).toList();
            service.chat("session", profile, new Prompt(List.of(new SystemMessage("fixed"),
                    new SystemMessage("memory B"), new UserMessage("time B"), assistant,
                    toolResults.getFirst(), toolResults.getLast()), options));
            var second = mapper.readTree(captured.get());
            var firstMessages = dash ? first.path("input").path("messages") : first.path("messages");
            var secondMessages = dash ? second.path("input").path("messages") : second.path("messages");
            assertThat(firstMessages.get(0)).isEqualTo(secondMessages.get(0));
            assertThat(secondMessages.get(0).path("content").asText()).isEqualTo("fixed");
            assertThat(firstMessages).isNotEqualTo(secondMessages);
            assertThat(secondMessages.get(3).path("tool_calls").get(0).path("id").asText()).isEqualTo("first");
            assertThat(secondMessages.get(3).path("tool_calls").get(1).path("id").asText()).isEqualTo("second");
            assertThat(secondMessages.get(4).path("tool_call_id").asText()).isEqualTo("first");
            assertThat(secondMessages.get(5).path("tool_call_id").asText()).isEqualTo("second");
            assertThat(secondMessages.get(5).path("content").asText()).contains("truncated");
            var firstTools = (dash ? first.path("parameters") : first).path("tools");
            var secondTools = (dash ? second.path("parameters") : second).path("tools");
            assertThat(firstTools.size()).isEqualTo(1);
            assertThat(firstTools).isEqualTo(secondTools);

            var summaryOptions = ToolCallingChatOptions.builder().maxTokens(384)
                    .internalToolExecutionEnabled(false).build();
            service.chat("session", profile, new Prompt(List.of(new UserMessage("history")), summaryOptions),
                    new Profile.ProviderRef("summary", "summary-model", null), LlmCallPurpose.SUMMARY);
            var summaryWire = mapper.readTree(captured.get());
            var parameters = dash ? summaryWire.path("parameters") : summaryWire;
            assertThat(parameters.path("max_tokens").asInt()).isEqualTo(384);
            assertThat(parameters.path("tools").size()).isZero();
            assertThat(summaryWire.path("model").asText()).isEqualTo("summary-model");
            assertThat(calls).extracting(io.oryxos.core.audit.LlmCallRecord::purpose)
                    .containsExactly(LlmCallPurpose.CHAT, LlmCallPurpose.CHAT, LlmCallPurpose.SUMMARY);
            assertThat(calls.getLast().provider()).isEqualTo("summary");
        } finally {
            server.stop(0);
        }
    }

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
