package io.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspaceServiceTest {
    @TempDir
    Path parent;

    @Test
    void initializeCreatesCompleteStructureAndNeverOverwritesExistingFiles() throws Exception {
        Path root = parent.resolve("workspace");
        var service = service(root);
        service.initialize();

        for (String directory : List.of("agents", "skills", "output", "memory", "sessions", "logs")) {
            assertThat(root.resolve(directory)).isDirectory();
        }
        assertThat(root.resolve("memory/MEMORY.md")).isRegularFile();
        for (String bootstrap : List.of("AGENTS.md", "SOUL.md", "USER.md")) {
            assertThat(root.resolve(bootstrap)).isRegularFile();
        }

        Files.writeString(root.resolve("AGENTS.md"), "用户内容");
        for (int i = 0; i < 10; i++) {
            service.initialize();
        }
        assertThat(Files.readString(root.resolve("AGENTS.md"))).isEqualTo("用户内容");
    }

    @Test
    void initializationTypeConflictsFailAndStatusDoesNotRepairThem() throws Exception {
        Path root = Files.createDirectory(parent.resolve("workspace"));
        Files.writeString(root.resolve("agents"), "占位文件");
        var service = service(root);

        assertThatThrownBy(service::initialize).isInstanceOf(IllegalStateException.class);
        var status = service.status();
        assertThat(status.initialized()).isFalse();
        assertThat(root.resolve("skills")).doesNotExist();
        assertThat(Files.readString(root.resolve("agents"))).isEqualTo("占位文件");
    }

    @Test
    void profileLifecycleIsSortedParseableAndArchivesWholeDirectoryWithoutCollision() throws Exception {
        Path root = parent.resolve("workspace");
        var service = service(root);
        service.initialize();
        service.createProfile("zeta");
        service.createProfile("Alpha");
        Files.writeString(root.resolve("agents/zeta/attachment.txt"), "保留");

        assertThat(service.listProfiles()).containsExactly("Alpha", "zeta");
        assertThat(service.showProfile("zeta")).contains("name: zeta", "model: qwen-plus");
        assertThatThrownBy(() -> service.createProfile("zeta")).isInstanceOf(IllegalStateException.class);

        Path first = service.archiveProfile("zeta");
        service.createProfile("zeta");
        Path second = service.archiveProfile("zeta");
        assertThat(first).isDirectory();
        assertThat(first.resolve("attachment.txt")).hasContent("保留");
        assertThat(second).isDirectory().isNotEqualTo(first);
        assertThat(second.getFileName().toString()).endsWith("-1");
        assertThat(service.listProfiles()).containsExactly("Alpha");
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "..", "a/b", "a\\b", "C:\\escape", "\\\\server\\share",
            "CON", "nul.txt", " bad", "bad ", "-dash"})
    void invalidNamesAreRejectedWithoutTouchingOutsideSentinel(String name) throws Exception {
        Path root = parent.resolve("workspace");
        var service = service(root);
        service.initialize();
        Path sentinel = parent.resolve("sentinel.txt");
        Files.writeString(sentinel, "安全");

        assertThatThrownBy(() -> service.createProfile(name)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(sentinel)).isEqualTo("安全");
        assertThat(root.resolve("agents")).isEmptyDirectory();
    }

    @Test
    void missingProfilesAndLinkedAgentTargetsAreRejected() throws Exception {
        Path root = parent.resolve("workspace");
        var service = service(root);
        service.initialize();
        assertThatThrownBy(() -> service.showProfile("missing")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.archiveProfile("missing")).isInstanceOf(IllegalStateException.class);

        Path outside = Files.createDirectory(parent.resolve("outside"));
        try {
            Files.createSymbolicLink(root.resolve("agents/linked"), outside);
            assertThatThrownBy(() -> service.showProfile("linked")).isInstanceOf(IllegalStateException.class);
            assertThat(outside).isEmptyDirectory();
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            // 当前 Windows 未启用符号链接权限时，其余路径边界用例仍然有效。
        }
    }

    private WorkspaceService service(Path root) {
        return new WorkspaceService(root,
                Clock.fixed(Instant.parse("2026-10-28T01:02:03Z"), ZoneOffset.UTC));
    }
}
