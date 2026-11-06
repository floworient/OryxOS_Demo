package io.oryxos.web.memory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.memory.MemorySnapshot;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.web.api.GlobalExceptionHandler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MemoryApiControllerTest {

    private final ProfileRegistry profiles = new ProfileRegistry();

    private final MemoryService memory = mock(MemoryService.class);

    private final Profile profile = profile("daily-tech");

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        profiles.register(profile);
        mvc = MockMvcBuilders.standaloneSetup(
                        new MemoryApiController(new MemoryQueryService(profiles, memory)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsBoundedViewWithStableEnvelopeAndMappedFields() throws Exception {
        MemoryOwner owner = MemoryOwner.agent("daily-tech");
        when(memory.loadLongTerm(profile)).thenReturn(new MemorySnapshot(owner,
                List.of(entry("core-1", owner, MemoryScope.CORE, "核心事实", "2026-10-31T01:30:00Z")),
                List.of(entry("archive-1", owner, MemoryScope.ARCHIVAL, "关注 AI", "2026-10-31T01:31:00Z")),
                true, "markdown", MemoryCapabilities.KEYWORD));

        mvc.perform(get("/api/v1/agents/daily-tech/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.owner").value("daily-tech"))
                .andExpect(jsonPath("$.data.backend").value("markdown"))
                .andExpect(jsonPath("$.data.capabilities").value("KEYWORD"))
                .andExpect(jsonPath("$.data.archivalTruncated").value(true))
                .andExpect(jsonPath("$.data.core[0].id").value("core-1"))
                .andExpect(jsonPath("$.data.core[0].content").value("核心事实"))
                .andExpect(jsonPath("$.data.archival[0].createdAt")
                        .value("2026-10-31T01:31:00Z"));
        verify(memory).loadLongTerm(profile);
    }

    @Test
    void returnsEmptyViewWithoutSideEffects() throws Exception {
        MemoryOwner owner = MemoryOwner.agent("daily-tech");
        when(memory.loadLongTerm(profile)).thenReturn(new MemorySnapshot(owner, List.of(), List.of(),
                false, "sqlite", MemoryCapabilities.KEYWORD));

        mvc.perform(get("/api/v1/agents/daily-tech/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.core").isEmpty())
                .andExpect(jsonPath("$.data.archival").isEmpty());
        verify(memory).loadLongTerm(profile);
    }

    @Test
    void queryAndCompatibilityRoutesUseTheSameProjection() throws Exception {
        MemoryOwner owner = MemoryOwner.agent("daily-tech");
        when(memory.loadLongTerm(profile)).thenReturn(new MemorySnapshot(owner, List.of(), List.of(),
                false, "sqlite", MemoryCapabilities.KEYWORD));

        mvc.perform(get("/api/v1/memory").queryParam("agentName", "daily-tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value("daily-tech"));
        mvc.perform(get("/api/v1/agents/daily-tech/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value("daily-tech"));
    }

    @Test
    void mapsUnknownAgentTo404WithoutReadingMemory() throws Exception {
        mvc.perform(get("/api/v1/agents/missing/memory"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_NOT_FOUND"));
        verifyNoInteractions(memory);
    }

    @Test
    void mapsMemoryFailureToSanitized503() throws Exception {
        when(memory.loadLongTerm(profile)).thenThrow(new MemoryException(
                MemoryException.Code.STORE_UNAVAILABLE, "upstream secret-api-key"));

        mvc.perform(get("/api/v1/agents/daily-tech/memory"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEMORY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("记忆服务暂不可用"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-api-key"))));
    }

    private static MemoryEntry entry(String id, MemoryOwner owner, MemoryScope scope,
            String content, String createdAt) {
        return new MemoryEntry(id, owner, scope, content, Instant.parse(createdAt));
    }

    private static Profile profile(String name) {
        return new Profile(name, "测试", new Profile.Identity(name, "测试"),
                new Profile.ProviderRef("test", "test", null), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(3, 10), Path.of(name, "AGENT.md"));
    }
}
