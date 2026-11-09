package io.oryxos.web.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.scheduler.AgentScheduler;
import io.oryxos.core.scheduler.InMemoryScheduledTaskStore;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskDefinition;
import io.oryxos.core.scheduler.TaskExecution;
import io.oryxos.core.scheduler.TaskExecutionView;
import io.oryxos.web.api.GlobalExceptionHandler;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScheduleApiControllerTest {

    private static final String V1 = "/api/v1/schedules";

    private static final String V2 = "/api/v2/schedules";

    private final InMemoryScheduledTaskStore store = new InMemoryScheduledTaskStore();

    private final AgentScheduler scheduler = mock(AgentScheduler.class);

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ScheduleApiController(scheduler, store))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void managementUsesStableIdentityAndRetiredHistoryRemainsQueryable() throws Exception {
        var id = add("weather", "daily");
        var execution = TaskExecution.succeeded(id, "session-1", Instant.EPOCH, Instant.EPOCH.plusMillis(14));
        when(scheduler.runNow(id)).thenReturn(Optional.of(TaskExecutionView.from(execution)));
        mvc.perform(get(V2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].scheduleId").value(id.value()));
        mvc.perform(put(V2 + "/" + id.value()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(post(V2 + "/" + id.value() + "/run")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMs").value(14));
        mvc.perform(post("/api/v2/agents/weather/schedules/daily/run")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduleId").value(id.value()));

        store.recordExecution(execution);
        store.retire(id);
        mvc.perform(get(V2 + "/" + id.value() + "/executions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"));
        mvc.perform(post(V2 + "/" + id.value() + "/run")).andExpect(status().isNotFound());
        mvc.perform(put(V2 + "/" + id.value()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}")).andExpect(status().isNotFound());
    }

    @Test
    void legacyKeyRequestsRejectAmbiguityAndUseSingleActiveMatch() throws Exception {
        var first = add("a", "daily");
        var second = add("b", "daily");
        var result = TaskExecutionView.from(TaskExecution.succeeded(first, null, Instant.EPOCH, Instant.EPOCH));
        when(scheduler.runNow(first)).thenReturn(Optional.of(result));
        mvc.perform(get(V1)).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get(V1 + "/daily/executions")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_KEY_CONFLICT"));
        mvc.perform(post(V1 + "/daily/run")).andExpect(status().isConflict());
        mvc.perform(put(V1 + "/daily").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")).andExpect(status().isConflict());
        store.retire(second);
        mvc.perform(post(V1 + "/daily/run")).andExpect(status().isOk());
        mvc.perform(put(V1 + "/daily").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get(V1 + "/daily/executions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void validatesIdentityAndBooleanAndMapsBusyAndTimeout() throws Exception {
        var id = add("weather", "daily");
        var task = V2 + "/" + id.value();
        mvc.perform(get(V2 + "/bad-id/executions")).andExpect(status().isBadRequest());
        mvc.perform(get(V2 + "/" + ScheduleId.from("absent", "daily").value() + "/executions"))
                .andExpect(status().isNotFound());
        for (String invalid : new String[]{"{}", "{\"enabled\":\"false\"}", "{"}) {
            mvc.perform(put(task).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        when(scheduler.runNow(id)).thenReturn(Optional.empty()).thenThrow(new TimeoutException());
        mvc.perform(post(task + "/run")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_BUSY"));
        mvc.perform(post(task + "/run")).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AGENT_TIMEOUT"));
    }

    private ScheduleId add(String profile, String key) {
        ScheduleId id = ScheduleId.from(profile, key);
        store.reconcile(new ScheduledTaskDefinition(id, profile, key, "0 0 8 * * *",
                ZoneId.of("Asia/Shanghai"), "查天气"));
        return id;
    }
}
