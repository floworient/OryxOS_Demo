package io.oryxos.web.memory;

import io.oryxos.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryApiController {

    private final MemoryQueryService service;

    public MemoryApiController(MemoryQueryService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/v1/memory", produces = "application/json")
    public ApiResponse<MemoryView> query(@RequestParam String agentName) {
        return ApiResponse.success(service.get(agentName));
    }

    @GetMapping(value = "/api/v1/agents/{name}/memory", produces = "application/json")
    public ApiResponse<MemoryView> compatibility(@PathVariable("name") String name) {
        return ApiResponse.success(service.get(name));
    }
}
