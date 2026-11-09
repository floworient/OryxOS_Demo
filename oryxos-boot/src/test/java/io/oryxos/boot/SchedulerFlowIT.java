package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.scheduler.AgentScheduler;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.session.SessionManager;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("integration")
@EnabledIfSystemProperty(named = "oryxos.scheduler.it", matches = "true")
class SchedulerFlowIT {

    @TempDir
    Path workspace;

    @Test
    void realWeatherThenWebhookReusesSessionAndRecordsBlockedDelivery() throws Exception {
        Path configuration = SchedulerAcceptanceConfig.prepare(workspace);
        prepareAgent();
        var received = new ConcurrentLinkedQueue<String>();
        var receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/hook", exchange -> {
            try (exchange) {
                received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
            }
        });
        receiver.start();
        try {
            var app = new SpringApplication(ScheduledTaskE2ETest.RuntimeUnderTest.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            try (var context = app.run("--oryxos.workspace=" + workspace,
                    "--spring.config.additional-location=" + configuration.toUri(),
                    "--oryxos.http.allowed-domains[0]=api.open-meteo.com",
                    "--oryxos.http.allowed-domains[1]=127.0.0.1")) {
                var scheduler = context.getBean(AgentScheduler.class);
                var jdbc = context.getBean(JdbcTemplate.class);
                var sessions = context.getBean(SessionManager.class);
                var channels = context.getBean(NotifyChannelService.class);
                String hook = "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook";
                channels.create(new NotifyChannelService.CreateCommand("team", "webhook", hook, null, Map.of()));
                var id = ScheduleId.from("real-weather", "daily");
                String sessionId = null;
                for (int round = 0; round < 4; round++) {
                    boolean blocked = round == 2;
                    channels.update("team", new NotifyChannelService.UpdateCommand(
                            "webhook", blocked ? "https://example.invalid/hook" : hook, null, Map.of()));
                    long toolBefore = jdbc.queryForObject(
                            "select coalesce(max(id),0) from tool_invocations", Long.class);
                    long llmBefore = jdbc.queryForObject("select coalesce(max(id),0) from llm_calls", Long.class);
                    long messagesBefore = sessionId == null ? 0 : assistantMessages(sessions, sessionId);
                    int notifyBefore = received.size();

                    var run = scheduler.runNow(id).orElseThrow();
                    assertThat(run.success()).isTrue();
                    if (sessionId == null) {
                        sessionId = run.sessionId();
                    }
                    assertThat(run.sessionId()).isEqualTo(sessionId);
                    var audit = jdbc.queryForList("""
                            select tool_name, success from tool_invocations where id>? and session_id=? order by id
                            """, toolBefore, sessionId);
                    assertThat(audit).hasSize(2);
                    assertThat(audit.get(0).get("tool_name")).isEqualTo("http_get");
                    assertThat(audit.get(0).get("success").toString()).isEqualTo("1");
                    assertThat(audit.get(1).get("tool_name")).isEqualTo("notify");
                    assertThat(audit.get(1).get("success").toString()).isEqualTo(blocked ? "0" : "1");
                    var calls = jdbc.queryForList("""
                            select status from llm_calls where id>? and session_id=? order by id
                            """, String.class, llmBefore, sessionId);
                    assertThat(calls).hasSize((int) (assistantMessages(sessions, sessionId) - messagesBefore))
                            .hasSizeGreaterThanOrEqualTo(3).doesNotContain("FAILED");
                    assertThat(received).hasSize(notifyBefore + (blocked ? 0 : 1));
                }
                assertThat(received).allSatisfy(payload -> assertThat(payload).contains("content"));
                assertThat(jdbc.queryForObject("""
                        select count(*) from sessions where channel='scheduler' and profile_name='real-weather'
                        """, Integer.class)).isEqualTo(1);
            }
        } finally {
            receiver.stop(0);
        }
    }

    private static long assistantMessages(SessionManager sessions, String id) {
        return sessions.details(id).orElseThrow().messages().stream()
                .filter(AssistantMessage.class::isInstance).count();
    }

    private void prepareAgent() throws Exception {
        Path directory = Files.createDirectories(workspace.resolve("agents/real-weather"));
        Files.writeString(directory.resolve("AGENT.md"), """
                ---
                name: real-weather
                description: 真实天气调度验收
                identity: {agent_name: 天气助手, prompt: 使用工具获取天气并推送}
                provider: {name: qwen, model: qwen-plus}
                tools: [http_get, notify]
                schedules:
                  - {key: daily, cron: "0 0 8 1 1 *", zone: Asia/Shanghai, message: "查北京天气并推送穿搭"}
                ---
                每次触发先调用 http_get，一次即可。
                URL 为 https://api.open-meteo.com/v1/forecast?latitude=39.9&longitude=116.4&current=temperature_2m
                收到天气后，调用 notify 向 team 渠道推送带温度和穿搭建议的文本，一次即可。
                推送工具成功或失败后都直接用文字报告结果，不重试。
                """);
    }

}
