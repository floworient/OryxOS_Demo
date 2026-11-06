package io.oryxos.web.system;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderCatalog;
import io.oryxos.core.tool.ToolRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SystemApiControllerTest {

    @Test
    void healthIsStaticAndInfoReturnsSafeSortedCountsWithDevFallback() throws Exception {
        var profiles = new ProfileRegistry();
        ToolRegistry tools = mock(ToolRegistry.class);
        ProviderCatalog providers = mock(ProviderCatalog.class);
        when(tools.registrations()).thenReturn(List.of());
        when(providers.configuredNames()).thenReturn(Set.of("zeta", "alpha"));
        var mvc = MockMvcBuilders.standaloneSetup(
                new SystemApiController(profiles, tools, providers)).build();

        mvc.perform(get("/api/v1/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
        verifyNoInteractions(providers);
        mvc.perform(get("/api/v1/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.implementationVersion").value("dev"))
                .andExpect(jsonPath("$.data.profileCount").value(0))
                .andExpect(jsonPath("$.data.toolCount").value(0))
                .andExpect(jsonPath("$.data.providers[0]").value("alpha"));
    }
}
