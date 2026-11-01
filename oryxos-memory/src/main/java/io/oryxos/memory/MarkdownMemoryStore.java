package io.oryxos.memory;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryEntryPolicy;
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
import java.nio.file.DirectoryStream;
import java.nio.file.OpenOption;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    private final WriteBoundaryHook writeBoundaryHook;

    private final ConcurrentHashMap<MemoryOwner, ReentrantLock> ownerLocks = new ConcurrentHashMap<>();

    public MarkdownMemoryStore(Path workspace, Clock clock, ZoneId zoneId,
            int archivalMaxCharacters, int recallMaxResults, int entryMaxCharacters) {
        this(workspace, clock, zoneId, archivalMaxCharacters, recallMaxResults,
                entryMaxCharacters, (stage, parent) -> { });
    }

    MarkdownMemoryStore(Path workspace, Clock clock, ZoneId zoneId,
            int archivalMaxCharacters, int recallMaxResults, int entryMaxCharacters,
            WriteBoundaryHook writeBoundaryHook) {
        this.workspace = Objects.requireNonNull(workspace, "工作区不能为空").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.zoneId = Objects.requireNonNull(zoneId, "时区不能为空");
        requirePositive(archivalMaxCharacters, "归档上下文上限");
        requirePositive(recallMaxResults, "召回数量上限");
        requirePositive(entryMaxCharacters, "单条记忆上限");
        this.archivalMaxCharacters = archivalMaxCharacters;
        this.recallMaxResults = recallMaxResults;
        this.entryMaxCharacters = entryMaxCharacters;
        this.writeBoundaryHook = Objects.requireNonNull(writeBoundaryHook, "写入边界钩子不能为空");
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
        String normalized = MemoryEntryPolicy.normalizeContent(content, entryMaxCharacters);
        ReentrantLock lock = ownerLocks.computeIfAbsent(owner, ignored -> new ReentrantLock());
        lock.lock();
        try {
            Path target = resolveTarget(owner);
            Path parent = target.getParent();
            if (parent == null) {
                throw new MemoryException(MemoryException.Code.STORE_UNAVAILABLE,
                        "记忆文件必须位于目录中");
            }
            DirectoryIdentity identity = captureDirectory(parent);
            ParsedMemory parsed = read(target, owner, true);
            List<MemoryEntry> core = new ArrayList<>(parsed.core());
            List<MemoryEntry> archival = new ArrayList<>(parsed.archival());
            Instant now = clock.instant();
            MemoryEntry entry = new MemoryEntry("markdown:pending", owner, scope, normalized, now);
            (scope == MemoryScope.CORE ? core : archival).add(entry);
            atomicWrite(target, render(core, archival), identity);
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
        return MemoryEntryPolicy.latestKeywordMatches(archivalEntries(owner), keyword, recallMaxResults);
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
        return MemoryEntryPolicy.newestWithinPromptLimit(entries, archivalMaxCharacters);
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
                String identity = owner.key() + "\n" + scope + "\n" + createdAt + "\n" + matcher.group(2);
                entries.add(new MemoryEntry(markdownId(scope, identity, entries.size()),
                        owner, scope, matcher.group(2), createdAt));
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

    private static String markdownId(MemoryScope scope, String identity, int sequence) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return "markdown:" + scope.name().toLowerCase(Locale.ROOT) + ":"
                    + String.format(Locale.ROOT, "%010d", sequence) + ":"
                    + HexFormat.of().formatHex(digest, 0, 8);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private void atomicWrite(Path target, String content, DirectoryIdentity identity) {
        verifyDirectory(identity);
        try {
            writeBoundaryHook.at(WriteStage.BEFORE_TEMPORARY_CREATE, identity.parent());
        }
        catch (IOException exception) {
            throw unavailable("无法验证记忆目录", exception);
        }
        verifyDirectory(identity);
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(identity.parent())) {
            verifyDirectory(identity);
            if (directory instanceof SecureDirectoryStream<Path> secure) {
                secureAtomicWrite(secure, target.getFileName(), content, identity);
            }
            else {
                verifiedAtomicWrite(target, content, identity);
            }
        }
        catch (MemoryException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw unavailable("无法写入记忆文件", exception);
        }
    }

    private void secureAtomicWrite(SecureDirectoryStream<Path> directory, Path targetName,
            String content, DirectoryIdentity identity) throws IOException {
        Path temporaryName = Path.of(".memory-" + java.util.UUID.randomUUID() + ".tmp");
        try {
            Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (var channel = directory.newByteChannel(temporaryName, options)) {
                channel.write(StandardCharsets.UTF_8.encode(content));
            }
            writeBoundaryHook.at(WriteStage.BEFORE_REPLACE, identity.parent());
            verifyDirectory(identity);
            try {
                directory.move(temporaryName, directory, targetName);
            }
            catch (java.nio.file.FileAlreadyExistsException exception) {
                secureReplaceExisting(directory, temporaryName, targetName);
            }
        }
        finally {
            try {
                directory.deleteFile(temporaryName);
            }
            catch (java.nio.file.NoSuchFileException ignored) {
                // 已完成替换，无临时文件需要清理。
            }
        }
    }

    private static void secureReplaceExisting(SecureDirectoryStream<Path> directory,
            Path temporaryName, Path targetName) throws IOException {
        Path backupName = Path.of(".memory-backup-" + java.util.UUID.randomUUID() + ".tmp");
        directory.move(targetName, directory, backupName);
        try {
            directory.move(temporaryName, directory, targetName);
            directory.deleteFile(backupName);
        }
        catch (IOException exception) {
            try {
                directory.move(backupName, directory, targetName);
            }
            catch (IOException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            throw exception;
        }
    }

    private void verifiedAtomicWrite(Path target, String content, DirectoryIdentity identity) {
        Path temporary = null;
        try {
            verifyDirectory(identity);
            temporary = Files.createTempFile(identity.parent(), ".memory-", ".tmp");
            verifyDirectory(identity);
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            writeBoundaryHook.at(WriteStage.BEFORE_REPLACE, identity.parent());
            verifyDirectory(identity);
            moveReplacing(temporary, target, Files::move);
            verifyDirectory(identity);
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

    private DirectoryIdentity captureDirectory(Path parent) {
        try {
            Path absolute = Objects.requireNonNull(parent, "记忆文件必须位于目录中")
                    .toAbsolutePath().normalize();
            Path real = absolute.toRealPath(LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes attributes = Files.readAttributes(absolute,
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || Files.isSymbolicLink(absolute)
                    || !real.startsWith(realWorkspace)) {
                throw new MemoryException(MemoryException.Code.STORE_UNAVAILABLE, "记忆目录不安全");
            }
            return new DirectoryIdentity(absolute, real, attributes.fileKey(), attributes.creationTime());
        }
        catch (MemoryException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw unavailable("记忆目录不存在或不可访问", exception);
        }
    }

    private void verifyDirectory(DirectoryIdentity expected) {
        DirectoryIdentity actual = captureDirectory(expected.parent());
        if (!actual.realPath().equals(expected.realPath())
                || !Objects.equals(actual.fileKey(), expected.fileKey())
                || !actual.creationTime().equals(expected.creationTime())) {
            throw new MemoryException(MemoryException.Code.STORE_UNAVAILABLE, "记忆目录在写入期间发生变化");
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

    enum WriteStage {
        BEFORE_TEMPORARY_CREATE,
        BEFORE_REPLACE
    }

    @FunctionalInterface
    interface WriteBoundaryHook {
        void at(WriteStage stage, Path parent) throws IOException;
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

    private record DirectoryIdentity(Path parent, Path realPath, Object fileKey,
            java.nio.file.attribute.FileTime creationTime) {
    }
}
