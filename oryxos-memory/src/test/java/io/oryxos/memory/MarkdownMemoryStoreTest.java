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
        Files.writeString(temporary.resolve("agents/first/MEMORY.md"), skeleton());
        Files.writeString(temporary.resolve("agents/second/MEMORY.md"), skeleton());
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
                .contains("## 核心记忆", "## 归档记忆", "用户关注 AI", "oryxos-memory-id:");
    }

    @Test
    void loadingMissingMemoryHasNoFilesystemSideEffect() throws IOException {
        Path target = temporary.resolve("agents/first/MEMORY.md");
        Files.delete(target);

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
                .isInstanceOf(MemoryException.class);
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
        Files.delete(temporary.resolve("agents/first/MEMORY.md"));
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

    @Test
    void persistedIdsSurviveDeletionAndReorderingOfEarlierEntries() throws IOException {
        memory.append(firstOwner(), "first", MemoryScope.ARCHIVAL);
        memory.append(firstOwner(), "second", MemoryScope.ARCHIVAL);
        memory.append(firstOwner(), "third", MemoryScope.ARCHIVAL);
        var before = memory.archivalEntries(firstOwner());
        Path target = temporary.resolve("agents/first/MEMORY.md");
        var lines = new java.util.ArrayList<>(Files.readAllLines(target));
        lines.removeIf(line -> line.endsWith(" first"));
        int second = indexEndingWith(lines, " second");
        int third = indexEndingWith(lines, " third");
        java.util.Collections.swap(lines, second, third);
        Files.write(target, lines);

        var after = memory.archivalEntries(firstOwner());
        assertThat(after).extracting(item -> item.id())
                .containsExactlyInAnyOrder(before.get(1).id(), before.get(2).id());
    }

    @Test
    void rejectsDirectoryReplacementAfterReadHandleIsOpened() throws IOException {
        AtomicBoolean replaced = new AtomicBoolean();
        var guarded = new MarkdownMemoryStore(temporary, CLOCK, ZoneId.of("Asia/Shanghai"),
                80, 2, 512, (stage, parent) -> {
                    if (stage == MarkdownMemoryStore.WriteStage.BEFORE_READ_OPEN
                            && replaced.compareAndSet(false, true)) {
                        Files.move(parent, parent.resolveSibling("first-original"));
                        Files.createDirectory(parent);
                        Files.writeString(parent.resolve("MEMORY.md"), skeleton() + "external");
                    }
                });

        assertThatThrownBy(() -> guarded.load(firstOwner()))
                .isInstanceOf(MemoryException.class).hasMessageContaining("发生变化");
    }

    @Test
    void rejectsParentDirectoryReplacementImmediatelyBeforeFallbackWrite() throws IOException {
        AtomicBoolean replaced = new AtomicBoolean();
        Path originalFile = temporary.resolve("agents/first/MEMORY.md");
        String original = Files.readString(originalFile);
        var guarded = new MarkdownMemoryStore(temporary, CLOCK, ZoneId.of("Asia/Shanghai"),
                80, 2, 512, (stage, parent) -> {
                    if (stage == MarkdownMemoryStore.WriteStage.BEFORE_REPLACE
                            && replaced.compareAndSet(false, true)) {
                        try {
                            Files.move(parent, parent.resolveSibling("first-original"));
                            Files.createDirectory(parent);
                            Files.writeString(parent.resolve("MEMORY.md"), skeleton());
                        }
                        catch (java.nio.file.AccessDeniedException ignored) {
                            // Windows 用已打开句柄阻止目录替换，本身即满足安全边界。
                        }
                    }
                });

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                guarded.append(firstOwner(), "safe", MemoryScope.ARCHIVAL));
        Path preserved = temporary.resolve("agents/first-original/MEMORY.md");
        if (Files.exists(preserved)) {
            assertThat(failure).isInstanceOf(MemoryException.class);
            assertThat(Files.readString(preserved)).isEqualTo(original);
        }
        else {
            assertThat(failure).isNull();
            assertThat(Files.readString(originalFile)).contains("safe");
        }
    }

    private static int indexEndingWith(java.util.List<String> lines, String suffix) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).endsWith(suffix)) {
                return index;
            }
        }
        throw new AssertionError("未找到记忆行 " + suffix);
    }

    private static String skeleton() {
        return "# MEMORY\n\n## 核心记忆\n\n## 归档记忆\n";
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
