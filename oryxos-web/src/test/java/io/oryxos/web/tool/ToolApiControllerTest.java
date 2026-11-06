package io.oryxos.web.tool;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.core.tool.ToolSource;
import io.oryxos.web.api.GlobalExceptionHandler;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ToolApiControllerTest {

    @Test
    void returnsSortedSafeGlobalAndAgentVisibleTools() throws Exception {
        var profiles = new ProfileRegistry();
        var profile = profile();
        profiles.register(profile);
        ToolRegistry tools = mock(ToolRegistry.class);
        var zeta = registration("zeta");
        var alpha = registration("alpha");
        when(tools.registrations()).thenReturn(List.of(zeta, alpha));
        when(tools.visibleTo(profile)).thenReturn(List.of(zeta));
        var mvc = MockMvcBuilders.standaloneSetup(new ToolApiController(profiles, tools))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/tools")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("alpha"))
                .andExpect(content().string(not(containsString("inputSchema"))))
                .andExpect(content().string(not(containsString("secret-schema"))));
        mvc.perform(get("/api/v1/tools").queryParam("agentName", "agent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("zeta"));
        mvc.perform(get("/api/v1/tools").queryParam("agentName", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_NOT_FOUND"));
    }

    private static ToolRegistration registration(String name) {
        return new ToolRegistration(new OryxTool() {
            public String getName() { return name; }

            public String getDescription() { return "描述"; }

            public String getInputSchema() { return "secret-schema"; }

            public ToolResult execute(String input) { return new ToolResult(true, "ok", null, false); }
        }, ToolSource.JAVA, "bean-" + name);
    }

    private static Profile profile() {
        return new Profile("agent", "描述", new Profile.Identity("助手", "prompt"),
                new Profile.ProviderRef("p", "m", null), List.of("zeta"), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(3, 10), Path.of("agent/AGENT.md"));
    }
}
