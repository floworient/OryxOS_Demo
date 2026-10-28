package io.oryxos.cli;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 轻量工作区与 Agent 文件生命周期服务。 */
public final class WorkspaceService {
    private static final List<String> DIRECTORIES = List.of(
            "agents", "skills", "output", "memory", "sessions", "logs");

    private static final List<String> ROOT_FILES = List.of("AGENTS.md", "SOUL.md", "USER.md");

    private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final Set<String> WINDOWS_DEVICES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final Path root;

    private final Clock clock;

    public WorkspaceService(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
    }

    public InitResult initialize() {
        try {
            int existing = validateInitializationTargets();
            int created = 0;
            if (!exists(root)) {
                Files.createDirectories(root);
                created++;
            }
            requireRootDirectory();
            for (String directory : DIRECTORIES) {
                Path target = root.resolve(directory);
                if (!exists(target)) {
                    Files.createDirectory(target);
                    created++;
                }
            }
            for (String file : ROOT_FILES) {
                created += createFileIfMissing(root.resolve(file), bootstrapContent(file));
            }
            created += createFileIfMissing(root.resolve("memory/MEMORY.md"), "# MEMORY\n");
            return new InitResult(root, created, existing);
        } catch (IOException exception) {
            throw failure("初始化工作区失败", exception);
        }
    }

    public Status status() {
        boolean initialized = isSafeDirectory(root);
        for (String directory : DIRECTORIES) {
            initialized &= isSafeDirectory(root.resolve(directory));
        }
        for (String file : ROOT_FILES) {
            initialized &= isSafeFile(root.resolve(file));
        }
        initialized &= isSafeFile(root.resolve("memory/MEMORY.md"));
        int profiles = 0;
        try {
            profiles = listProfiles().size();
        } catch (IllegalStateException ignored) {
            initialized = false;
        }
        return new Status(root, initialized, profiles,
                Files.isRegularFile(root.resolve("oryxos.db"), LinkOption.NOFOLLOW_LINKS));
    }

    public List<String> listProfiles() {
        Path agents = root.resolve("agents");
        if (!exists(agents)) {
            return List.of();
        }
        Path agentsReal = requireDirectDirectory(agents, root, "agents 目录无效");
        try (var entries = Files.list(agentsReal)) {
            return entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path.resolve("AGENT.md"), LinkOption.NOFOLLOW_LINKS))
                    .map(WorkspaceService::fileName)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw failure("无法列出 Agent", exception);
        }
    }

    public Path createProfile(String name) {
        validateName(name);
        Path agents = requireAgentsDirectory();
        Path target = directChild(agents, name);
        if (exists(target)) {
            throw new IllegalStateException("Agent 已存在: " + name);
        }
        Path temporary = null;
        try {
            temporary = Files.createTempDirectory(agents, ".creating-");
            Files.writeString(temporary.resolve("AGENT.md"), template(name));
            move(temporary, target);
            return target;
        } catch (IOException exception) {
            cleanupTemporary(temporary);
            throw failure("创建 Agent 失败: " + name, exception);
        }
    }

    public String showProfile(String name) {
        Path directory = requireProfileDirectory(name);
        Path definition = directory.resolve("AGENT.md");
        requireDirectFile(definition, directory, "AGENT.md 无效");
        try {
            return Files.readString(definition);
        } catch (IOException exception) {
            throw failure("读取 Agent 失败: " + name, exception);
        }
    }

    public Path archiveProfile(String name) {
        Path source = requireProfileDirectory(name);
        Path archive = ensureArchiveDirectory();
        String base = name + "-" + clock.instant().toEpochMilli();
        Path target = directChild(archive, base);
        int suffix = 0;
        while (exists(target)) {
            suffix++;
            target = directChild(archive, base + "-" + suffix);
        }
        try {
            move(source, target);
            requireDirectDirectory(target, archive, "归档目标无效");
            return target;
        } catch (IOException exception) {
            throw failure("归档 Agent 失败: " + name, exception);
        }
    }

    private int validateInitializationTargets() throws IOException {
        int existing = 0;
        if (exists(root)) {
            requireRootDirectory();
            existing++;
        }
        for (String directory : DIRECTORIES) {
            Path target = root.resolve(directory);
            if (exists(target)) {
                requireExpectedDirectory(target, directory);
                existing++;
            }
        }
        for (String file : ROOT_FILES) {
            Path target = root.resolve(file);
            if (exists(target)) {
                requireExpectedFile(target, file);
                existing++;
            }
        }
        Path memory = root.resolve("memory/MEMORY.md");
        if (exists(memory)) {
            requireExpectedFile(memory, "memory/MEMORY.md");
            existing++;
        }
        return existing;
    }

    private void requireRootDirectory() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalStateException("工作区根必须是普通目录: " + root);
        }
        root.toRealPath();
    }

    private void requireExpectedDirectory(Path target, String label) throws IOException {
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IllegalStateException(label + " 必须是普通目录");
        }
        Path actualParent = target.toRealPath().getParent();
        if (exists(root) && !root.toRealPath().equals(actualParent)) {
            throw new IllegalStateException(label + " 越出工作区边界");
        }
    }

    private void requireExpectedFile(Path target, String label) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IllegalStateException(label + " 必须是普通文件");
        }
        Path expectedParent = label.contains("/") ? root.resolve("memory").toRealPath() : root.toRealPath();
        Path actualParent = target.toRealPath().getParent();
        if (!expectedParent.equals(actualParent)) {
            throw new IllegalStateException(label + " 越出工作区边界");
        }
    }

    private Path requireAgentsDirectory() {
        return requireDirectDirectory(root.resolve("agents"), root, "工作区尚未初始化 agents 目录");
    }

    private Path requireProfileDirectory(String name) {
        validateName(name);
        Path agents = requireAgentsDirectory();
        return requireDirectDirectory(directChild(agents, name), agents, "Agent 不存在或目录无效: " + name);
    }

    private Path ensureArchiveDirectory() {
        Path target = root.resolve("archive");
        if (exists(target)) {
            return requireDirectDirectory(target, root, "archive 目录无效");
        }
        try {
            Files.createDirectory(target);
            return requireDirectDirectory(target, root, "archive 目录无效");
        } catch (IOException exception) {
            throw failure("创建 archive 目录失败", exception);
        }
    }

    private static Path requireDirectDirectory(Path target, Path expectedParent, String message) {
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IllegalStateException(message);
        }
        try {
            Path parentReal = expectedParent.toRealPath();
            Path targetReal = target.toRealPath();
            if (!parentReal.equals(targetReal.getParent())) {
                throw new IllegalStateException(message);
            }
            return targetReal;
        } catch (IOException exception) {
            throw failure(message, exception);
        }
    }

    private static Path requireDirectFile(Path target, Path expectedParent, String message) {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IllegalStateException(message);
        }
        try {
            Path targetReal = target.toRealPath();
            if (!expectedParent.toRealPath().equals(targetReal.getParent())) {
                throw new IllegalStateException(message);
            }
            return targetReal;
        } catch (IOException exception) {
            throw failure(message, exception);
        }
    }

    private static Path directChild(Path parent, String name) {
        Path target = parent.resolve(name).normalize();
        if (!parent.equals(target.getParent())) {
            throw new IllegalArgumentException("路径越出工作区边界: " + name);
        }
        return target;
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalStateException("Agent 目录缺少文件名: " + path);
        }
        return fileName.toString();
    }

    private static void validateName(String name) {
        if (name == null || !PROFILE_NAME.matcher(name).matches() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Agent 名称非法: " + name);
        }
        String base = name.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (WINDOWS_DEVICES.contains(base)) {
            throw new IllegalArgumentException("Agent 名称是系统保留名: " + name);
        }
    }

    private static int createFileIfMissing(Path target, String content) throws IOException {
        if (exists(target)) {
            return 0;
        }
        Files.writeString(target, content);
        return 1;
    }

    private static String bootstrapContent(String file) {
        return "# " + file.substring(0, file.length() - 3) + "\n";
    }

    private static String template(String name) {
        return """
                ---
                name: ${name}
                description: 新建 Agent
                identity:
                  agent_name: ${name}
                  prompt: 请按照 Agent 指令完成任务
                provider:
                  name: qwen
                  model: qwen-plus
                tools: []
                mcp_servers: []
                channels:
                  - name: cli
                    config: {}
                schedules: []
                bootstrap:
                  - AGENTS.md
                  - SOUL.md
                  - USER.md
                settings:
                  max_iterations: 10
                  max_history_turns: 20
                ---
                请在此补充 Agent 的具体任务指令。
                """.replace("${name}", name);
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void cleanupTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary.resolve("AGENT.md"));
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // 原始创建异常保留为主错误。
        }
    }

    private static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isSafeDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    private static boolean isSafeFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    private static IllegalStateException failure(String message, Exception cause) {
        return new IllegalStateException(message + ": " + cause.getMessage(), cause);
    }

    public record InitResult(Path root, int created, int skipped) { }

    public record Status(Path root, boolean initialized, int agentCount, boolean databaseExists) { }
}
