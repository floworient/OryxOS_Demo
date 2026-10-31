package io.oryxos.memory;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemorySnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.CopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 以工作区 MEMORY.md 为本体的长期记忆存储。 */
public final class MarkdownMemoryStore implements LongTermMemoryStore {

    private static final String HEADER = "# MEMORY";

    private static final String CORE_HEADER = "## 核心记忆";

    private static final String ARCHIVAL_HEADER = "## 归档记忆";

    private static final String SKELETON = HEADER + "\n\n" + CORE_HEADER + "\n\n"
            + ARCHIVAL_HEADER + "\n";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern ENTRY = Pattern.compile("^- \\[(.+?)] (.+)$");

    private final Path workspace;

    private final Path realWorkspace;

    private final Clock clock;

    private final ZoneId zoneId;

    private final int archivalMaxCharacters;

    private final int recallMaxResults;

    private final int entryMaxCharacters;

    private final ConcurrentHashMap<MemoryOwner, ReentrantLock> ownerLocks = new ConcurrentHashMap<>();

    public MarkdownMemoryStore(Path workspace, Clock clock, ZoneId zoneId,
            int archivalMaxCharacters, int recallMaxResults, int entryMaxCharacters) {
        this.workspace = Objects.requireNonNull(workspace, "工作区不能为空").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.zoneId = Objects.requireNonNull(zoneId, "时区不能为空");
        requirePositive(archivalMaxCharacters, "归档上下文上限");
        requirePositive(recallMaxResults, "召回数量上限");
        requirePositive(entryMaxCharacters, "单条记忆上限");
        this.archivalMaxCharacters = archivalMaxCharacters;
        this.recallMaxResults = recallMaxResults;
        this.entryMaxCharacters = entryMaxCharacters;
        try {
            this.realWorkspace = this.workspace.toRealPath();
        }
        catch (IOException exception) {
            throw unavailable("无法访问记忆工作区", exception);
        }
    }

    @Override
    public void append(MemoryOwner owner, String content, MemoryScope scope) {
        Objects.requireNonNull(owner, "记忆 owner 不能为空");
        Objects.requireNonNull(scope, "记忆 scope 不能为空");
        String normalized = normalizeContent(content);
        if (normalized.length() > entryMaxCharacters) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT,
                    "记忆内容超过单条长度上限");
        }
        ReentrantLock lock = ownerLocks.computeIfAbsent(owner, ignored -> new ReentrantLock());
        lock.lock();
        try {
            Path target = resolveTarget(owner);
            ParsedMemory parsed = read(target, owner, true);
            List<MemoryEntry> core = new ArrayList<>(parsed.core());
            List<MemoryEntry> archival = new ArrayList<>(parsed.archival());
            Instant now = clock.instant();
            MemoryEntry entry = new MemoryEntry("markdown:pending", owner, scope, normalized, now);
            (scope == MemoryScope.CORE ? core : archival).add(entry);
            atomicWrite(target, render(core, archival));
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public MemorySnapshot load(MemoryOwner owner) {
        ParsedMemory parsed = read(resolveTarget(owner), owner, false);
        List<MemoryEntry> selected = newestCompleteEntries(parsed.archival());
        return new MemorySnapshot(owner, parsed.core(), selected,
                selected.size() < parsed.archival().size(), "markdown", capabilities());
    }

    @Override
    public List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "召回关键词不能为空");
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        List<MemoryEntry> matches = archivalEntries(owner).stream()
                .filter(entry -> entry.content().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .toList();
        int from = Math.max(0, matches.size() - recallMaxResults);
        return List.copyOf(matches.subList(from, matches.size()));
    }

    @Override
    public MemoryCapabilities capabilities() {
        return MemoryCapabilities.KEYWORD;
    }

    @Override
    public List<MemoryEntry> archivalEntries(MemoryOwner owner) {
        return read(resolveTarget(owner), owner, false).archival();
    }

    private List<MemoryEntry> newestCompleteEntries(List<MemoryEntry> entries) {
        List<MemoryEntry> selectedReverse = new ArrayList<>();
        int used = 0;
        for (int index = entries.size() - 1; index >= 0; index--) {
            MemoryEntry entry = entries.get(index);
            int length = renderEntry(entry).length();
            if (used + length > archivalMaxCharacters) {
                break;
            }
            selectedReverse.add(entry);
            used += length;
        }
        selectedReverse.sort(Comparator.comparing(MemoryEntry::createdAt));
        return List.copyOf(selectedReverse);
    }

    private ParsedMemory read(Path target, MemoryOwner owner, boolean allowLegacySkeleton) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return ParsedMemory.empty();
        }
        requireSafeTarget(target);
        try {
            String source = Files.readString(target, StandardCharsets.UTF_8).replace("\r\n", "\n");
            if (allowLegacySkeleton && source.equals(HEADER + "\n")) {
                return ParsedMemory.empty();
            }
            return parse(source, owner);
        }
        catch (MemoryException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw unavailable("无法读取记忆文件", exception);
        }
    }

    private ParsedMemory parse(String source, MemoryOwner owner) {
        String[] lines = source.split("\n", -1);
        if (lines.length < 5 || !HEADER.equals(lines[0])) {
            throw malformed();
        }
        int coreHeader = findUnique(lines, CORE_HEADER);
        int archivalHeader = findUnique(lines, ARCHIVAL_HEADER);
        if (coreHeader <= 0 || archivalHeader <= coreHeader) {
            throw malformed();
        }
        requireBlankRange(lines, 1, coreHeader);
        List<MemoryEntry> core = parseEntries(lines, coreHeader + 1, archivalHeader, owner, MemoryScope.CORE);
        List<MemoryEntry> archival = parseEntries(lines, archivalHeader + 1, lines.length,
                owner, MemoryScope.ARCHIVAL);
        return new ParsedMemory(core, archival);
    }

    private static int findUnique(String[] lines, String header) {
        int found = -1;
        for (int index = 0; index < lines.length; index++) {
            if (header.equals(lines[index])) {
                if (found >= 0) {
                    throw malformed();
                }
                found = index;
            }
        }
        return found;
    }

    private static void requireBlankRange(String[] lines, int start, int end) {
        for (int index = start; index < end; index++) {
            if (!lines[index].isBlank()) {
                throw malformed();
            }
        }
    }

    private List<MemoryEntry> parseEntries(String[] lines, int start, int end,
            MemoryOwner owner, MemoryScope scope) {
        List<MemoryEntry> entries = new ArrayList<>();
        for (int index = start; index < end; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            Matcher matcher = ENTRY.matcher(lines[index]);
            if (!matcher.matches()) {
                throw malformed();
            }
            try {
                Instant createdAt = LocalDateTime.parse(matcher.group(1), TIMESTAMP)
                        .atZone(zoneId).toInstant();
                entries.add(new MemoryEntry("markdown:" + scope.name().toLowerCase(Locale.ROOT)
                        + ":" + index, owner, scope, matcher.group(2), createdAt));
            }
            catch (DateTimeParseException | IllegalArgumentException exception) {
                throw malformed();
            }
        }
        return List.copyOf(entries);
    }

    private String render(List<MemoryEntry> core, List<MemoryEntry> archival) {
        StringBuilder builder = new StringBuilder(SKELETON);
        int archivalHeaderStart = builder.indexOf(ARCHIVAL_HEADER);
        StringBuilder coreLines = new StringBuilder();
        core.forEach(entry -> coreLines.append(renderEntry(entry)));
        builder.insert(archivalHeaderStart, coreLines);
        archival.forEach(entry -> builder.append(renderEntry(entry)));
        return builder.toString();
    }

    private String renderEntry(MemoryEntry entry) {
        return "- [" + TIMESTAMP.format(entry.createdAt().atZone(zoneId)) + "] "
                + entry.content() + "\n";
    }

    private Path resolveTarget(MemoryOwner owner) {
        Objects.requireNonNull(owner, "记忆 owner 不能为空");
        Path parent = owner.agentScoped()
                ? workspace.resolve("agents").resolve(owner.key())
                : workspace.resolve("memory");
        Path target = parent.resolve("MEMORY.md").toAbsolutePath().normalize();
        if (!target.startsWith(workspace)) {
            throw new MemoryException(MemoryException.Code.INVALID_OWNER, "记忆路径越出工作区");
        }
        requireSafeParent(parent);
        return target;
    }

    private void requireSafeParent(Path parent) {
        try {
            Path realParent = parent.toRealPath();
            if (!realParent.startsWith(realWorkspace) || Files.isSymbolicLink(parent)) {
                throw new MemoryException(MemoryException.Code.STORE_UNAVAILABLE, "记忆目录不安全");
            }
        }
        catch (IOException exception) {
            throw unavailable("记忆目录不存在或不可访问", exception);
        }
    }

    private static void requireSafeTarget(Path target) {
        if (Files.isSymbolicLink(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new MemoryException(MemoryException.Code.STORE_UNAVAILABLE, "记忆文件不是安全的普通文件");
        }
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "记忆内容不能为空");
        }
        String normalized = content.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "记忆内容不能为空");
        }
        return normalized;
    }

    private void atomicWrite(Path target, String content) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), ".memory-", ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            moveReplacing(temporary, target, Files::move);
        }
        catch (IOException exception) {
            throw unavailable("无法写入记忆文件", exception);
        }
        finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                }
                catch (IOException ignored) {
                    // 临时文件清理由后续运维处理，不覆盖原始写入异常。
                }
            }
        }
    }

    static void moveReplacing(Path source, Path target, MoveOperation operation) throws IOException {
        try {
            operation.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException exception) {
            operation.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    interface MoveOperation {
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须大于 0");
        }
    }

    private static MemoryException malformed() {
        return new MemoryException(MemoryException.Code.MALFORMED_STORE, "MEMORY.md 格式无效");
    }

    private static MemoryException unavailable(String message, Exception cause) {
        return new MemoryException(MemoryException.Code.STORE_UNAVAILABLE, message, cause);
    }

    private record ParsedMemory(List<MemoryEntry> core, List<MemoryEntry> archival) {
        private static ParsedMemory empty() {
            return new ParsedMemory(List.of(), List.of());
        }
    }
}
