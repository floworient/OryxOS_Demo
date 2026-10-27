package io.oryxos.core.context;

import io.oryxos.core.profile.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class ContextLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextLoader.class);

    private final Path workspace;

    public ContextLoader(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    public String load(Profile profile) {
        try {
            Path root = workspace.toRealPath();
            Path agents = checked(root.resolve("agents"), root);
            Path source = profile.sourcePath();
            Path agent = parent(source);
            if (Files.isSymbolicLink(source) || Files.isSymbolicLink(agent)
                    || !parent(agent).toRealPath().equals(agents)) {
                throw new IOException("Agent 路径非法");
            }
            source = checked(source, agents);
            String text = Files.readString(source);
            if (!text.isEmpty() && text.charAt(0) == 0xFEFF) {
                text = text.substring(1);
            }
            String[] lines = text.split("\\R", -1);
            int end = frontmatterEnd(lines);
            StringBuilder content = new StringBuilder(profile.identity().prompt()).append('\n')
                    .append(String.join("\n", java.util.Arrays.copyOfRange(lines, end + 1, lines.length)));
            for (String file : profile.bootstrap()) {
                Path path = root.resolve(file).normalize();
                if (Path.of(file).isAbsolute() || !path.startsWith(root)) {
                    throw new IOException("Bootstrap 路径越界: " + path);
                }
                if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                    LOGGER.warn("Bootstrap 缺失 agentName={} path={}", profile.name(), path);
                }
                else {
                    content.append('\n').append(Files.readString(checked(path, root)));
                }
            }
            appendSkills(content, parent(source), root);
            return content.toString();
        }
        catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("上下文加载失败 agentName=" + profile.name()
                    + " path=" + profile.sourcePath() + " reason=" + exception.getMessage(), exception);
        }
    }

    private static Path parent(Path path) {
        return java.util.Objects.requireNonNull(path.getParent(), "上下文路径缺少父目录");
    }

    private static Path checked(Path path, Path boundary) throws IOException {
        Path real = path.toRealPath();
        if (!real.startsWith(boundary) || real.equals(boundary)) {
            throw new IOException("路径越界: " + path);
        }
        return real;
    }

    private static int frontmatterEnd(String[] lines) throws IOException {
        if (lines.length == 0 || !lines[0].trim().equals("---")) {
            throw new IOException("缺少 frontmatter 起始边界");
        }
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].trim().equals("---")) {
                return index;
            }
        }
        throw new IOException("缺少 frontmatter 结束边界");
    }

    private static void appendSkills(StringBuilder content, Path agent, Path root) throws IOException {
        Path bindings = agent.resolve("skills");
        if (Files.notExists(bindings, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(bindings, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Skill 绑定目录必须为普通目录: " + bindings);
        }
        checked(bindings, agent);
        Path lexicalRoot = root.resolve("skills").normalize();
        Path publicRoot = null;
        try (var entries = Files.list(bindings)) {
            for (Path entry : entries.sorted().toList()) {
                Path relative = referenceTarget(entry);
                Path declared = parent(entry).resolve(relative).normalize();
                if (!declared.startsWith(lexicalRoot) || declared.equals(lexicalRoot)) {
                    throw new IOException("Skill 引用目标越出公共目录: " + entry);
                }
                if (publicRoot == null) {
                    publicRoot = checked(lexicalRoot, root);
                }
                Path target = checked(declared, publicRoot);
                if (!Files.isDirectory(target)) {
                    throw new IOException("Skill 目标不是目录: " + entry);
                }
                Path skill = checked(target.resolve("SKILL.md"), target);
                Map<?, ?> metadata = metadata(skill);
                String name = field(metadata, "name");
                String description = field(metadata, "description");
                content.append("\nSkill: ").append(name).append("\nDescription: ").append(description)
                        .append("\nPath: ").append(skill);
            }
        }
    }

    private static Path referenceTarget(Path entry) throws IOException {
        if (Files.isSymbolicLink(entry)) {
            throw new IOException("原生 Skill 软连接请迁移为普通 .link 相对引用文件: " + entry);
        }
        String name = java.util.Objects.requireNonNull(entry.getFileName()).toString();
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                || !name.endsWith(".link") || name.length() <= 5 || Files.size(entry) > 4096) {
            throw new IOException("Skill 绑定必须是小于等于4096字节的普通 .link 文件: " + entry);
        }
        String text = Files.readString(entry);
        if (text.startsWith("\ufeff")) {
            text = text.substring(1);
        }
        text = text.strip();
        if (text.isEmpty() || text.lines().count() != 1 || text.startsWith("/") || text.startsWith("\\")
                || text.indexOf(':') >= 0) {
            throw new IOException("Skill 引用必须仅含一条相对路径，禁止绝对、盘符和UNC路径: " + entry);
        }
        Path relative = Path.of(text.replace('\\', '/'));
        if (relative.isAbsolute()) {
            throw new IOException("Skill 引用禁止绝对路径: " + entry);
        }
        return relative;
    }

    private static Map<?, ?> metadata(Path skill) throws IOException {
        StringBuilder yamlText = new StringBuilder();
        try (var reader = Files.newBufferedReader(skill)) {
            String first = reader.readLine();
            if (first != null && !first.isEmpty() && first.charAt(0) == 0xFEFF) {
                first = first.substring(1);
            }
            if (!"---".equals(first)) {
                throw new IOException("Skill 缺少 frontmatter: " + skill);
            }
            String line;
            boolean ended = false;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    ended = true;
                    break;
                }
                yamlText.append(line).append('\n');
                if (yamlText.length() > 65536) {
                    throw new IOException("Skill 元数据过长: " + skill);
                }
            }
            if (!ended) {
                throw new IOException("Skill 元数据边界缺失: " + skill);
            }
        }
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(10);
        options.setNestingDepthLimit(10);
        Object data = new Yaml(new SafeConstructor(options)).load(yamlText.toString());
        if (!(data instanceof Map<?, ?> map)) {
            throw new IOException("Skill 元数据非法: " + skill);
        }
        return map;
    }

    private static String field(Map<?, ?> metadata, String name) throws IOException {
        if (!(metadata.get(name) instanceof String value) || value.isBlank()) {
            throw new IOException("Skill 元数据字段非法: " + name);
        }
        return value;
    }
}
