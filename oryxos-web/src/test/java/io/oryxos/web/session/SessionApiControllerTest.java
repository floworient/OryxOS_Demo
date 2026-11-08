package io.oryxos.web.session;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.core.session.SessionStatus;
import io.oryxos.web.api.AgentCallTimeoutException;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SessionApiControllerTest {

    private final SessionWebService service = mock(SessionWebService.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SessionApiController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createReturns201WithLocationOr200WhenReused() throws Exception {
        var view = view("session-1");
        when(service.create("daily-tech", "user-1"))
                .thenReturn(new SessionWebService.Creation(view, true),
                        new SessionWebService.Creation(view, false));

        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"daily-tech\",\"userId\":\"user-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sessions/session-1"))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("session-1"));

        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"daily-tech\",\"userId\":\"user-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("session-1"));
    }

    @Test
    void exposesMessageGetAndArchiveWithUnifiedEnvelopeAndSafeFields() throws Exception {
        var view = view("session-1");
        when(service.send("session-1", "hello"))
                .thenReturn(new MessageReplyView("session-1", "reply"));
        when(service.get("session-1")).thenReturn(view);
        when(service.archive("session-1"))
                .thenReturn(new SessionArchiveView("session-1", SessionStatus.ARCHIVED));

        mvc.perform(post("/api/v1/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("reply"));
        mvc.perform(get("/api/v1/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].toolCalls[0].name").value("lookup"))
                .andExpect(content().string(not(containsString("arguments"))))
                .andExpect(content().string(not(containsString("responseData"))))
                .andExpect(content().string(not(containsString("sourcePath"))));
        mvc.perform(delete("/api/v1/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void listsSafeSessionSummariesInUnifiedEnvelope() throws Exception {
        var summary = new SessionSummaryView("session-1", "daily-tech", "web", "user-1",
                SessionStatus.ACTIVE, Instant.parse("2026-11-04T00:00:00Z"),
                Instant.parse("2026-11-04T00:01:00Z"), 4);
        when(service.list("active")).thenReturn(List.of(summary));

        mvc.perform(get("/api/v1/sessions").queryParam("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].id").value("session-1"))
                .andExpect(jsonPath("$.data[0].messageCount").value(4))
                .andExpect(content().string(not(containsString("messages"))));
    }

    @Test
    void rejectsInvalidRequestsBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"\",\"userId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/sessions/session-1/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void mapsApplicationProviderAndTimeoutErrors() throws Exception {
        when(service.get("missing")).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "会话不存在"));
        when(service.send("busy", "hello")).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "SESSION_BUSY", "会话正在处理其他操作"));
        when(service.send("provider", "hello")).thenThrow(new ProviderException(
                ProviderErrorCode.NETWORK_ERROR, "p", "m", "canary-secret", null));
        when(service.send("timeout", "hello")).thenThrow(new AgentCallTimeoutException());

        mvc.perform(get("/api/v1/sessions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        postMessage("busy").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_BUSY"));
        postMessage("provider").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("canary-secret"))));
        postMessage("timeout").andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AGENT_TIMEOUT"));
    }

    private org.springframework.test.web.servlet.ResultActions postMessage(String id) throws Exception {
        return mvc.perform(post("/api/v1/sessions/{id}/messages", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"));
    }

    private static SessionView view(String id) {
        return new SessionView(id, "web", "user-1", "daily-tech", SessionStatus.ACTIVE,
                Instant.parse("2026-11-04T00:00:00Z"), Instant.parse("2026-11-04T00:01:00Z"),
                null, false, List.of(new MessageView("assistant", "reply",
                        List.of(new MessageView.ToolReferenceView("lookup", "call-1")), List.of())));
    }
}
