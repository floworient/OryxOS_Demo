package io.oryxos.web.profile;

import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.web.api.ApiResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/profiles", produces = "application/json")
public final class ProfileApiController {

    private final ProfileRegistry profiles;

    public ProfileApiController(ProfileRegistry profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ApiResponse<List<ProfileView>> list() {
        return ApiResponse.success(profiles.all().stream()
                .sorted(Comparator.comparing(io.oryxos.core.profile.Profile::name))
                .map(ProfileView::from).toList());
    }
}
