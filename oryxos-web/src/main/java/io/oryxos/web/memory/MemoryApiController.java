package io.oryxos.web.memory;

import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class MemoryApiController {

    private final ProfileRegistry profiles;

    private final MemoryService memory;

    public MemoryApiController(ProfileRegistry profiles, MemoryService memory) {
        this.profiles = profiles;
        this.memory = memory;
    }

    @GetMapping("/{name}/memory")
    public ApiResponse<MemoryView> get(@PathVariable("name") String name) {
        Profile profile = profiles.find(name).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
        return ApiResponse.success(MemoryView.from(memory.loadLongTerm(profile)));
    }
}
