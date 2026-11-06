package io.oryxos.web.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsProviderFailureToSanitized503() throws Exception {
        mvc.perform(get("/test-errors/provider"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("模型服务暂不可用"))
                .andExpect(content().string(not(containsString("canary-secret"))));
    }

    @Test
    void mapsAgentDeadlineToSanitized504() throws Exception {
        mvc.perform(get("/test-errors/timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AGENT_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("Agent 调用超时"));
    }

    @Test
    void mapsSessionStorageFailureToSanitized500() throws Exception {
        mvc.perform(get("/test-errors/storage"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"))
                .andExpect(jsonPath("$.message").value("会话存储暂不可用"))
                .andExpect(content().string(not(containsString("canary-secret"))));
    }

    @RestController
    static class FailureController {

        @GetMapping("/test-errors/provider")
        String provider() {
            throw new ProviderException(ProviderErrorCode.NETWORK_ERROR, "provider", "model",
                    "canary-secret", null);
        }

        @GetMapping("/test-errors/timeout")
        String timeout() {
            throw new AgentCallTimeoutException();
        }

        @GetMapping("/test-errors/storage")
        String storage() {
            throw new SessionStorageException("canary-secret", new IllegalStateException("db"));
        }
    }
}
