package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.agent.AgentService;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.scheduler.AgentScheduler;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskStore;
import io.oryxos.core.session.SessionManager;
import io.oryxos.provider.ProviderDefinition;
import io.oryxos.provider.ProviderRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

class MultiAgentScheduleTest {

    @TempDir
    Path workspace;

    @Test
    void isolatesToolsSessionsAndCronFailuresAndReconcilesWeatherAudits() throws Exception {
        writeAgent("files", "read_file", "0 0 8 1 1 *", "fail", "scripted");
        writeAgent("http", "http_get", "0 0 9 1 1 *", "ok", "scripted");
        writeAgent("weather", "http_get, notify", "0 0 10 1 1 *", "weather", "scripted");
        var received = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/weather", exchange -> {
            try (exchange) {
                var body = "{\"temperature\":12}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.createContext("/notify", exchange -> {
            try (exchange) {
                if (new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                        .contains("12")) {
                    received.incrementAndGet();
                }
                exchange.sendResponseHeaders(204, -1);
            }
        });
        server.start();
        try {
            String local = "http://127.0.0.1:" + server.getAddress().getPort();
            var app = new SpringApplication(ScheduledTaskE2ETest.RuntimeUnderTest.class, ScriptedModel.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            try (var context = app.run("--oryxos.workspace=" + workspace,
                    "--oryxos.providers[0].name=scripted", "--oryxos.providers[0].type=MOCK",
                    "--oryxos.providers[0].model=scripted", "--oryxos.http.allowed-domains[0]=127.0.0.1",
                    "--test.weather-url=" + local + "/weather")) {
                var sessions = context.getBean(SessionManager.class);
                var agents = context.getBean(AgentService.class);
                var a = sessions.getOrCreate("cli", "user", "files");
                var b = sessions.getOrCreate("cli", "user", "http");
                assertThat(agents.process(a, "A only")).isEqualTo("files:read_file");
                assertThat(agents.process(b, "B only")).isEqualTo("http:http_get");
                assertThat(sessions.details(a.id()).orElseThrow().messages().getFirst().getText())
                        .isEqualTo("A only");
                assertThat(sessions.details(b.id()).orElseThrow().messages().getFirst().getText())
                        .isEqualTo("B only");

                var store = context.getBean(ScheduledTaskStore.class);
                var cron = context.getBean(Capture.class);
                cron.callbacks.get("0 0 8 1 1 *").run();
                cron.callbacks.get("0 0 9 1 1 *").run();
                assertThat(store.executionViews(ScheduleId.from("files", "daily")))
                        .singleElement().satisfies(run -> assertThat(run.success()).isFalse());
                assertThat(store.executionViews(ScheduleId.from("http", "daily")))
                        .singleElement().satisfies(run -> assertThat(run.success()).isTrue());

                var channels = context.getBean(NotifyChannelService.class);
                channels.create(new NotifyChannelService.CreateCommand(
                        "team", "webhook", local + "/notify", null, Map.of()));
                var scheduler = context.getBean(AgentScheduler.class);
                var weather = ScheduleId.from("weather", "daily");
                cron.callbacks.get("0 0 10 1 1 *").run();
                var session = store.executionViews(weather).getFirst().sessionId();
                var jdbc = context.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject(
                        "select count(*) from llm_calls where session_id=?", Integer.class, session)).isEqualTo(3);
                assertThat(jdbc.queryForList("""
                        select tool_name from tool_invocations where session_id=? and success=1 order by id
                        """, String.class, session)).containsExactly("http_get", "notify");
                assertThat(received).hasValue(1);

                channels.update("team", new NotifyChannelService.UpdateCommand(
                        "webhook", "https://example.invalid/notify", null, Map.of()));
                assertThat(scheduler.runNow(weather).orElseThrow().sessionId()).isEqualTo(session);
                assertThat(jdbc.queryForList("""
                        select tool_name from tool_invocations where session_id=? and success=0
                        """, String.class, session)).containsExactly("notify");
                assertThat(received).hasValue(1);

                channels.update("team", new NotifyChannelService.UpdateCommand(
                        "webhook", local + "/notify", null, Map.of()));
                assertThat(scheduler.runNow(weather).orElseThrow().sessionId()).isEqualTo(session);
                assertThat(received).hasValue(2);
                assertThat(jdbc.queryForObject("""
                        select count(*) from sessions where channel='scheduler' and profile_name='weather'
                        """, Integer.class)).isEqualTo(1);
            }
        } finally {
            server.stop(0);
        }
    }

    private void writeAgent(String name, String tools, String cron, String message, String provider) throws Exception {
        Path directory = Files.createDirectories(workspace.resolve("agents").resolve(name));
        Files.writeString(directory.resolve("AGENT.md"), """
                ---
                name: %s
                description: 定时隔离
                identity: {agent_name: 隔离助手, prompt: 执行任务}
                provider: {name: %s, model: scripted}
                tools: [%s]
                schedules:
                  - {key: daily, cron: "%s", zone: Asia/Shanghai, message: "%s"}
                ---
                执行本 Agent 的任务。
                """.formatted(name, provider, tools, cron, message));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedModel {
        @Bean
        @Primary
        ProviderRegistry scriptedProvider(org.springframework.core.env.Environment environment) {
            var definition = new ProviderDefinition(
                    "scripted", ProviderDefinition.Type.MOCK, "scripted", null, null);
            return new ProviderRegistry(List.of(definition),
                    Map.of("scripted", prompt -> respond(prompt, environment.getRequiredProperty("test.weather-url"))));
        }

        @Bean
        @Primary
        Capture scheduleCallbacks() {
            return new Capture();
        }
    }

    static class Capture extends ThreadPoolTaskScheduler {
        private final Map<String, Runnable> callbacks = new ConcurrentHashMap<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            if (trigger instanceof CronTrigger cron) {
                callbacks.put(cron.getExpression(), task);
            }
            return super.schedule(task, trigger);
        }
    }

    private static ChatResponse respond(Prompt prompt, String weatherUrl) {
        var turn = prompt.getInstructions().reversed();
        String text = turn.stream().filter(UserMessage.class::isInstance).findFirst().orElseThrow().getText();
        if ("fail".equals(text)) {
            throw new IllegalStateException("故意失败");
        }
        if (!"weather".equals(text)) {
            var options = (ToolCallingChatOptions) prompt.getOptions();
            var names = options.getToolCallbacks().stream().map(callback -> callback.getToolDefinition().name())
                    .toList();
            return response(new AssistantMessage(ProfileContext.current().name() + ":" + String.join(",", names)));
        }
        var results = turn.stream().takeWhile(message -> !(message instanceof UserMessage))
                .filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast).toList();
        if (results.isEmpty()) {
            return tool("weather-read", "http_get", Map.of("url", weatherUrl));
        }
        if (results.size() == 1) {
            assertThat(results.getFirst().getResponses().getFirst().responseData()).contains("12");
            return tool("weather-send", "notify", Map.of("channel", "team", "content", "12 度，建议加外套"));
        }
        return response(new AssistantMessage("天气任务结束"));
    }

    private static ChatResponse tool(String id, String name, Map<String, String> arguments) {
        return response(AssistantMessage.builder().content("准备执行")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, JsonParser.toJson(arguments))))
                .build());
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }
}
