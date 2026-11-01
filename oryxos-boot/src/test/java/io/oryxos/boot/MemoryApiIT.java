package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.OryxOsApplication;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = OryxOsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MemoryApiIT {

    private static final Path WORKSPACE = temporaryWorkspace();

    private static final Profile PROFILE = profile();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProfileRegistry profiles;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("oryxos.workspace", WORKSPACE::toString);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORKSPACE.resolve("runtime.db"));
    }

    @BeforeEach
    void setUp() {
        when(profiles.find("daily-tech")).thenReturn(Optional.of(PROFILE));
        when(profiles.find("missing")).thenReturn(Optional.empty());
    }

    @Test
    void returnsRealMarkdownViewWithoutCreatingMemoryFile() throws Exception {
        Path memoryFile = WORKSPACE.resolve("agents/daily-tech/MEMORY.md");
        assertThat(memoryFile).doesNotExist();

        mvc.perform(get("/api/v1/agents/daily-tech/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner").value("daily-tech"))
                .andExpect(jsonPath("$.data.backend").value("markdown"))
                .andExpect(jsonPath("$.data.core").isEmpty())
                .andExpect(jsonPath("$.data.archival").isEmpty());

        assertThat(memoryFile).doesNotExist();
    }

    @Test
    void returns404ForUnknownAgent() throws Exception {
        mvc.perform(get("/api/v1/agents/missing/memory"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_NOT_FOUND"));
    }

    private static Path temporaryWorkspace() {
        try {
            Path workspace = Files.createTempDirectory("oryxos-memory-api-");
            Files.createDirectories(workspace.resolve("agents/daily-tech"));
            return workspace;
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Profile profile() {
        return new Profile("daily-tech", "科技日报", new Profile.Identity("日报助手", "生成日报"),
                new Profile.ProviderRef("unused", "unused", null), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(3, 10),
                WORKSPACE.resolve("agents/daily-tech/AGENT.md"));
    }
}
