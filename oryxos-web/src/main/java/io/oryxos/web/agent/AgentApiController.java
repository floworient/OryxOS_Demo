package io.oryxos.web.agent;

import io.oryxos.web.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 一次性同步调用 Agent 的 REST 适配器。 */
@RestController
@RequestMapping(value = "/api/v1/agents", produces = "application/json")
public final class AgentApiController {

    private final AgentInvocationWebService service;

    public AgentApiController(AgentInvocationWebService service) {
        this.service = service;
    }

    @PostMapping("/{name}/invoke")
    public ApiResponse<InvokeAgentView> invoke(@PathVariable String name,
            @Valid @RequestBody InvokeAgentRequest request) {
        return ApiResponse.success(service.invoke(name, request.message()));
    }
}
