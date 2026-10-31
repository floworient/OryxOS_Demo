package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.audit.ToolInvocationRecord;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.core.tool.ToolRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MemoryToolAuditIT {

    @TempDir
    Path workspace;

    @Test
    void defaultMarkdownToolsUseRegistryExecutorAuditAndAreVisibleNextLoad() throws Exception {
        Files.createDirectories(workspace.resolve("agents/alpha"));
        Files.createDirectories(workspace.resolve("memory"));
        var records = new ArrayList<ToolInvocationRecord>();

        new ApplicationContextRunner()
                .withUserConfiguration(MemoryConfiguration.class, ToolConfiguration.class)
                .withPropertyValues("oryxos.workspace=" + workspace)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var registry = context.getBean(ToolRegistry.class);
                    assertThat(registry.registrations()).extracting(item -> item.name())
                            .contains("save_memory", "recall_memory");
                    Profile profile = org.mockito.Mockito.mock(Profile.class);
                    org.mockito.Mockito.when(profile.name()).thenReturn("alpha");
                    org.mockito.Mockito.when(profile.tools()).thenReturn(List.of("save_memory", "recall_memory"));
                    ProfileContext.set(profile);
                    try (var executor = new ToolExecutor(registry, (p, call) -> { }, records::add)) {
                        assertThat(executor.execute("s", new ToolCallIntent("c", "save_memory",
                                "{\"content\":\"持久偏好\"}")).success()).isTrue();
                    }
                    finally {
                        ProfileContext.clear();
                    }
                    assertThat(records).hasSize(1);
                    assertThat(context.getBean(MemoryService.class).loadLongTerm(profile).archival())
                            .extracting(entry -> entry.content()).containsExactly("持久偏好");
                });
    }
}
