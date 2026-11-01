package io.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.LongTermMemoryStoreContract;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class MarkdownMemoryStoreTest extends LongTermMemoryStoreContract {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-31T01:30:00Z"), ZoneId.of("Asia/Shanghai"));

    @TempDir
    Path temporary;

    private MarkdownMemoryStore memory;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(temporary.resolve("agents/first"));
        Files.createDirectories(temporary.resolve("agents/second"));
        Files.createDirectories(temporary.resolve("memory"));
        memory = new MarkdownMemoryStore(temporary, CLOCK, ZoneId.of("Asia/Shanghai"), 80, 2, 512);
    }

    @Override
    protected LongTermMemoryStore store() {
        return memory;
    }

    @Override
    protected MemoryOwner firstOwner() {
        return MemoryOwner.agent("first");
    }

    @Override
    protected MemoryOwner secondOwner() {
        return MemoryOwner.agent("second");
    }

    @Test
    void upgradesEmptyLegacySkeletonWhenFirstAppending() throws IOException {
        Path target = temporary.resolve("agents/first/MEMORY.md");
        Files.writeString(target, "# MEMORY\n");

        memory.append(firstOwner(), "用户关注 AI", MemoryScope.ARCHIVAL);

        assertThat(Files.readString(target))
                .contains("## 核心记忆", "## 归档记忆", "- [2026-10-31 09:30:00] 用户关注 AI");
    }

    @Test
    void loadingMissingMemoryHasNoFilesystemSideEffect() {
        Path target = temporary.resolve("agents/first/MEMORY.md");

        assertThat(memory.load(firstOwner()).core()).isEmpty();
        assertThat(target).doesNotExist();
    }

    @Test
    void normalizesNewlinesSoContentCannotCreateSections() throws IOException {
        memory.append(firstOwner(), "line1\n## 归档记忆\r\nline2", MemoryScope.CORE);

        String content = Files.readString(temporary.resolve("agents/first/MEMORY.md"));
        assertThat(content).contains("line1 ## 归档记忆 line2");
        assertThat(content.lines().filter("## 归档记忆"::equals).count()).isEqualTo(1);
    }

    @Test
    void rejectsMalformedNonEmptyMemoryInsteadOfRewritingIt() throws IOException {
        Path target = temporary.resolve("agents/first/MEMORY.md");
        String malformed = "# MEMORY\n用户不可判定的旧内容\n";
        Files.writeString(target, malformed);

        assertThatThrownBy(() -> memory.append(firstOwner(), "new", MemoryScope.ARCHIVAL))
                .isInstanceOf(MemoryException.class)
                .hasMessageContaining("格式");
        assertThat(Files.readString(target)).isEqualTo(malformed);
    }

    @Test
    void reportsKeywordCapability() {
        assertThat(memory.capabilities()).isEqualTo(MemoryCapabilities.KEYWORD);
    }

    @Test
    void rejectsSymbolicLinkTargetWhenPlatformSupportsLinks() throws IOException {
        Path realFile = temporary.resolve("real-memory.md");
        Files.writeString(realFile, "# MEMORY\n");
        Path target = temporary.resolve("agents/first/MEMORY.md");
        try {
            Files.createSymbolicLink(target, realFile);
        }
        catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("当前平台不允许创建测试符号链接");
        }

        assertThatThrownBy(() -> memory.load(firstOwner()))
                .isInstanceOf(MemoryException.class)
                .hasMessageContaining("普通文件");
    }

    @Test
    void fallsBackWhenAtomicMoveIsUnavailable() throws IOException {
        Path source = temporary.resolve("source.tmp");
        Path target = temporary.resolve("target.md");
        Files.writeString(source, "memory");
        int[] attempts = {0};

        MarkdownMemoryStore.moveReplacing(source, target,
                (from, to, options) -> moveWithFirstAtomicFailure(from, to, attempts, options));

        assertThat(attempts[0]).isEqualTo(2);
        assertThat(Files.readString(target)).isEqualTo("memory");
    }

    @Test
    void rejectsParentDirectoryReplacementDuringWrite() throws IOException {
        AtomicBoolean replaced = new AtomicBoolean();
        var guarded = new MarkdownMemoryStore(temporary, CLOCK, ZoneId.of("Asia/Shanghai"),
                80, 2, 512, (stage, parent) -> {
                    if (stage == MarkdownMemoryStore.WriteStage.BEFORE_TEMPORARY_CREATE
                            && replaced.compareAndSet(false, true)) {
                        Files.move(parent, parent.resolveSibling("first-original"));
                        Files.createDirectory(parent);
                    }
                });

        assertThatThrownBy(() -> guarded.append(firstOwner(), "must not escape", MemoryScope.ARCHIVAL))
                .isInstanceOf(MemoryException.class).hasMessageContaining("发生变化");
        assertThat(temporary.resolve("agents/first/MEMORY.md")).doesNotExist();
        assertThat(temporary.resolve("agents/first-original/MEMORY.md")).doesNotExist();
    }

    @Test
    void archivalIdRemainsStableWhenCoreIsAppended() {
        memory.append(firstOwner(), "archival", MemoryScope.ARCHIVAL);
        String before = memory.load(firstOwner()).archival().getFirst().id();

        memory.append(firstOwner(), "core", MemoryScope.CORE);

        assertThat(memory.load(firstOwner()).archival().getFirst().id()).isEqualTo(before);
    }

    private static Path moveWithFirstAtomicFailure(Path source, Path target,
            int[] attempts, CopyOption... options) throws IOException {
        attempts[0]++;
        if (attempts[0] == 1) {
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "测试降级");
        }
        return Files.move(source, target, options);
    }
}
