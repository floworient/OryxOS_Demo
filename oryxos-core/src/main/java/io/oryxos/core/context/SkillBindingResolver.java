package io.oryxos.core.context;

import io.oryxos.core.profile.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class SkillBindingResolver {

    private final Path workspace;

    public SkillBindingResolver(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    public List<SkillBinding> resolve(Profile profile) {
        try {
            Path root = workspace.toRealPath();
            Path agents = checked(root.resolve("agents"), root);
            Path source = profile.sourcePath();
            Path agent = parent(source);
            if (Files.isSymbolicLink(source) || Files.isSymbolicLink(agent)
                    || !parent(agent).toRealPath().equals(agents)) {
                throw new IOException("Agent 路径非法");
            }
            checked(source, agents);
            return resolveBindings(agent, root);
        }
        catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Skill 绑定解析失败 agentName=" + profile.name()
                    + " path=" + profile.sourcePath() + " reason=" + exception.getMessage(), exception);
        }
    }

    private static List<SkillBinding> resolveBindings(Path agent, Path root) throws IOException {
        Path bindings = agent.resolve("skills");
        if (Files.notExists(bindings, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(bindings, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(bindings)) {
            throw new IOException("Skill 绑定目录必须为普通目录: " + bindings);
        }
        checked(bindings, agent.toRealPath());
        Path lexicalRoot = root.resolve("skills").normalize();
        Path publicRoot = checked(lexicalRoot, root);
        var result = new ArrayList<SkillBinding>();
        try (var entries = Files.list(bindings)) {
            for (Path entry : entries.sorted().toList()) {
                Path relative = referenceTarget(entry);
                Path declared = parent(entry).resolve(relative).normalize();
                if (!declared.startsWith(lexicalRoot) || declared.equals(lexicalRoot)) {
                    throw new IOException("Skill 引用目标越出公共目录: " + entry);
                }
                Path target = checked(declared, publicRoot);
                if (!Files.isDirectory(target) || Files.isSymbolicLink(declared)) {
                    throw new IOException("Skill 目标不是普通目录: " + entry);
                }
                Path declaredDefinition = target.resolve("SKILL.md");
                if (!Files.isRegularFile(declaredDefinition, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(declaredDefinition)) {
                    throw new IOException("Skill 定义必须是普通文件: " + declaredDefinition);
                }
                Path definition = checked(declaredDefinition, target);
                Map<?, ?> metadata = metadata(definition);
                result.add(new SkillBinding(field(metadata, "name"), field(metadata, "description"),
                        target, definition));
            }
        }
        return List.copyOf(result);
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
                if (yamlText.length() > 65_536) {
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

    private static Path checked(Path path, Path boundary) throws IOException {
        Path real = path.toRealPath();
        if (!real.startsWith(boundary) || real.equals(boundary)) {
            throw new IOException("路径越界: " + path);
        }
        return real;
    }

    private static Path parent(Path path) {
        return java.util.Objects.requireNonNull(path.getParent(), "路径缺少父目录");
    }
}
