package io.oryxos.web.sandbox;

import io.oryxos.core.tool.SandboxAllowlistReader;
import io.oryxos.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SandboxAllowlistController {

    private final SandboxAllowlistReader reader;

    public SandboxAllowlistController(SandboxAllowlistReader reader) {
        this.reader = reader;
    }

    @GetMapping(value = "/api/v1/sandbox/allowlist", produces = "application/json")
    public ApiResponse<SandboxAllowlistReader.Summary> allowlist() {
        return ApiResponse.success(reader.read());
    }
}
