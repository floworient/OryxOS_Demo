package io.oryxos.core.context;

import io.oryxos.core.profile.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextLoader.class);

    private final Path workspace;

    private final SkillBindingResolver skillBindings;

    public ContextLoader(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.skillBindings = new SkillBindingResolver(workspace);
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
            for (SkillBinding skill : skillBindings.resolve(profile)) {
                content.append("\nSkill: ").append(skill.name())
                        .append("\nDescription: ").append(skill.description())
                        .append("\nPath: ").append(skill.definitionFile());
            }
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

}
