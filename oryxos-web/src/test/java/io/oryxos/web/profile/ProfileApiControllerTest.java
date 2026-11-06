package io.oryxos.web.profile;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileApiControllerTest {

    @Test
    void returnsSortedSafeProfiles() throws Exception {
        var profiles = new ProfileRegistry();
        profiles.register(profile("zeta"));
        profiles.register(profile("alpha"));
        var mvc = MockMvcBuilders.standaloneSetup(new ProfileApiController(profiles)).build();

        mvc.perform(get("/api/v1/profiles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("alpha"))
                .andExpect(jsonPath("$.data[1].name").value("zeta"))
                .andExpect(content().string(not(containsString("secret-prompt"))))
                .andExpect(content().string(not(containsString("sourcePath"))))
                .andExpect(content().string(not(containsString("channels"))));
    }

    private static Profile profile(String name) {
        return new Profile(name, "描述", new Profile.Identity("助手", "secret-prompt"),
                new Profile.ProviderRef("provider", "model", null), List.of("lookup"),
                List.of("mcp"), List.of(new Profile.ChannelRef("web", java.util.Map.of())),
                List.of(), List.of(), new Profile.Settings(3, 10), Path.of(name, "AGENT.md"));
    }
}
