package io.oryxos.web.notify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.core.notify.NotifyChannelConflictException;
import io.oryxos.core.notify.NotifyChannelRegistry;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.web.api.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotifyChannelApiControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new NotifyChannelService(new MemoryRegistry(),
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(
                new NotifyChannelApiController(service, new NotifyChannelMasker()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void performsCrudWithStableEnvelopeAndMaskedSecret() throws Exception {
        String json = """
                {"name":"team","type":"webhook","url":"https://example.com/private-1234",
                 "description":"团队通知","config":{"token":"abcdef"}}
                """;
        mvc.perform(post("/api/v1/notify-channels").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.url").value("****1234"))
                .andExpect(jsonPath("$.data.config.token").value("****cdef"));
        mvc.perform(get("/api/v1/notify-channels/team"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("team"));
        mvc.perform(delete("/api/v1/notify-channels/team"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notify-channels/team"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFY_CHANNEL_NOT_FOUND"));
    }

    @Test
    void mapsValidationAndConflict() throws Exception {
        String valid = "{\"name\":\"team\",\"type\":\"webhook\",\"url\":\"${TEAM_URL}\"}";
        mvc.perform(post("/api/v1/notify-channels").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/notify-channels").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/notify-channels").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad/path\",\"type\":\"webhook\",\"url\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    private static final class MemoryRegistry implements NotifyChannelRegistry {
        private final Map<String, NotifyChannel> values = new LinkedHashMap<>();

        @Override
        public NotifyChannel create(NotifyChannel channel) {
            if (values.putIfAbsent(channel.name(), channel) != null) {
                throw new NotifyChannelConflictException("通知渠道名称已存在");
            }
            return channel;
        }

        @Override
        public NotifyChannel update(NotifyChannel channel) {
            values.put(channel.name(), channel);
            return channel;
        }

        @Override
        public Optional<NotifyChannel> find(String name) {
            return Optional.ofNullable(values.get(name));
        }

        @Override
        public List<NotifyChannel> list() {
            return List.copyOf(values.values());
        }

        @Override
        public void delete(String name) {
            values.remove(name);
        }
    }
}
