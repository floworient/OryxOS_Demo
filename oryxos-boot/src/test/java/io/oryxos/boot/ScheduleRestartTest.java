package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.scheduler.AgentScheduler;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskDefinition;
import io.oryxos.core.scheduler.ScheduledTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class ScheduleRestartTest {

    @TempDir
    Path workspace;

    @Test
    void toolListCannotReconcileSchedulesOwnedByRunningService() throws Exception {
        ScheduledTaskE2ETest.prepareAgent(workspace);
        Files.writeString(workspace.resolve("application.yaml"), """
                oryxos:
                  providers:
                    - {name: mock, type: MOCK, model: mock-memory}
                """);
        try (var service = ScheduledTaskE2ETest.launch(workspace)) {
            var store = service.getBean(ScheduledTaskStore.class);
            Path definition = workspace.resolve("agents/memory-agent/AGENT.md");
            Files.writeString(definition, Files.readString(definition).replace("key: daily", "key: edited"));
            try (var cli = new BootCliRuntime(ScheduledTaskE2ETest.RuntimeUnderTest.class, ignored -> { })) {
                assertThat(cli.tools(workspace)).isNotEmpty();
            }
            assertThat(store.list()).extracting(task -> task.scheduleKey()).containsExactly("daily");
        }
    }

    @Test
    void restoresSessionCoreMemoryScheduleStateHistoryAndAudit() throws Exception {
        ScheduledTaskE2ETest.prepareAgent(workspace);
        var id = ScheduleId.from("memory-agent", "daily");
        var stale = ScheduleId.from("memory-agent", "removed");
        String sessionId;
        List<Long> audit;
        try (var original = ScheduledTaskE2ETest.launch(workspace)) {
            original.getBean(LongTermMemoryStore.class)
                    .append(MemoryOwner.agent("memory-agent"), "重启后仍保留的核心偏好", MemoryScope.CORE);
            sessionId = original.getBean(AgentScheduler.class).runNow(id).orElseThrow().sessionId();
            var store = original.getBean(ScheduledTaskStore.class);
            store.setEnabled(id, false).orElseThrow();
            store.reconcile(new ScheduledTaskDefinition(stale, "memory-agent", "removed",
                    "0 0 8 * * *", ZoneId.of("UTC"), "已删除的定义"));
            audit = auditIds(original.getBean(JdbcTemplate.class), sessionId);
            assertThat(audit).hasSize(2);
        }

        try (var restarted = ScheduledTaskE2ETest.launch(workspace)) {
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) restarted).build();
            mvc.perform(get("/api/v1/sessions/" + sessionId)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.messages.length()").value(4));
            mvc.perform(get("/api/v1/memory").param("agentName", "memory-agent"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.core[0].content")
                            .value("重启后仍保留的核心偏好"));
            var store = restarted.getBean(ScheduledTaskStore.class);
            assertThat(store.list()).singleElement().satisfies(task -> {
                assertThat(task.scheduleId()).isEqualTo(id);
                assertThat(task.enabled()).isFalse();
                assertThat(task.runCount()).isEqualTo(1);
                assertThat(task.lastStatus()).isEqualTo("success");
                assertThat(task.nextRunAt()).isAfter(Instant.now());
            });
            assertThat(store.executionViews(id)).singleElement()
                    .satisfies(run -> assertThat(run.sessionId()).isEqualTo(sessionId));
            assertThat(store.exists(stale)).isTrue();
            assertThat(store.find(stale)).isEmpty();
            var jdbc = restarted.getBean(JdbcTemplate.class);
            assertThat(auditIds(jdbc, sessionId)).containsExactlyElementsOf(audit);
            assertThat(restarted.getBean(AgentScheduler.class).runNow(id).orElseThrow().sessionId())
                    .isEqualTo(sessionId);
            assertThat(auditIds(jdbc, sessionId)).startsWith(audit.toArray(Long[]::new)).hasSizeGreaterThan(2);
        }
    }

    private static List<Long> auditIds(JdbcTemplate jdbc, String sessionId) {
        return jdbc.queryForList("select id from llm_calls where session_id = ? order by id", Long.class, sessionId);
    }
}
