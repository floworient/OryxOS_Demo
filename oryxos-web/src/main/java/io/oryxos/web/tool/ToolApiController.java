package io.oryxos.web.tool;

import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.ApiResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/tools", produces = "application/json")
public final class ToolApiController {

    private final ProfileRegistry profiles;

    private final ToolRegistry tools;

    public ToolApiController(ProfileRegistry profiles, ToolRegistry tools) {
        this.profiles = profiles;
        this.tools = tools;
    }

    @GetMapping
    public ApiResponse<List<ToolView>> list(
            @RequestParam(required = false) String agentName) {
        List<ToolRegistration> registrations;
        if (agentName == null) {
            registrations = tools.registrations();
        }
        else {
            var profile = profiles.find(agentName).orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
            registrations = tools.visibleTo(profile);
        }
        return ApiResponse.success(registrations.stream()
                .sorted(Comparator.comparing(ToolRegistration::name))
                .map(ToolView::from).toList());
    }
}
