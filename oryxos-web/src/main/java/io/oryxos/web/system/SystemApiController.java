package io.oryxos.web.system;

import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderCatalog;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.web.api.ApiResponse;
import java.util.Comparator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SystemApiController {

    private final ProfileRegistry profiles;

    private final ToolRegistry tools;

    private final ProviderCatalog providers;

    public SystemApiController(ProfileRegistry profiles, ToolRegistry tools,
            ProviderCatalog providers) {
        this.profiles = profiles;
        this.tools = tools;
        this.providers = providers;
    }

    @GetMapping(value = "/api/v1/health", produces = "application/json")
    public ApiResponse<HealthView> health() {
        return ApiResponse.success(new HealthView("UP"));
    }

    @GetMapping(value = "/api/v1/info", produces = "application/json")
    public ApiResponse<SystemInfoView> info() {
        String version = SystemApiController.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = "dev";
        }
        return ApiResponse.success(new SystemInfoView("OryxOS", version,
                System.getProperty("java.version"), profiles.all().size(),
                tools.registrations().size(), providers.configuredNames().stream()
                        .sorted(Comparator.naturalOrder()).toList()));
    }

    public record HealthView(String status) { }
}
