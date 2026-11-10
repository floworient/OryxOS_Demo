package io.oryxos.web.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.context.ContextBudgetExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ContextBudgetErrorTest {

    @Test
    void rendersBudgetRejectionUsingTheCommonResponse() throws Exception {
        MockMvcBuilders.standaloneSetup(new BudgetLimitedEndpoint())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build().perform(get("/test/context-limit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTEXT_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("模型输入超过上下文预算"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    static class BudgetLimitedEndpoint {
        @GetMapping("/test/context-limit")
        String invoke() {
            throw new ContextBudgetExceededException();
        }
    }
}
