package io.oryxos.web.agent;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.web.api.AgentCallTimeoutException;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentApiControllerTest {

    private final AgentInvocationWebService service = mock(AgentInvocationWebService.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AgentApiController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void invokesAgentAndReturnsOnlyReply() throws Exception {
        when(service.invoke("daily-tech", "hello")).thenReturn(new InvokeAgentView("reply"));

        invoke("daily-tech", "hello")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.reply").value("reply"))
                .andExpect(jsonPath("$.data.sessionId").doesNotExist());
    }

    @Test
    void validatesBlankAndUtf8Limit() throws Exception {
        invoke("daily-tech", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        String oversized = "你".repeat(10_923);
        when(service.invoke("daily-tech", oversized))
                .thenThrow(new IllegalArgumentException("消息不能超过 32768 UTF-8 字节"));
        invoke("daily-tech", oversized)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void mapsUnknownProviderAndTimeoutFailuresWithoutLeaks() throws Exception {
        when(service.invoke("missing", "hello")).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
        when(service.invoke("provider", "hello")).thenThrow(new ProviderException(
                ProviderErrorCode.NETWORK_ERROR, "p", "m", "canary-secret", null));
        when(service.invoke("timeout", "hello")).thenThrow(new AgentCallTimeoutException());

        invoke("missing", "hello").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_NOT_FOUND"));
        invoke("provider", "hello").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("canary-secret"))));
        invoke("timeout", "hello").andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AGENT_TIMEOUT"));
    }

    private org.springframework.test.web.servlet.ResultActions invoke(String name, String message)
            throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(java.util.Map.of("message", message));
        return mvc.perform(post("/api/v1/agents/{name}/invoke", name)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
