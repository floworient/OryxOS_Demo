package io.oryxos.core.profile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class AgentLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentLoader.class);

    private static final Set<String> LEGACY_FIELDS = Set.of("skills", "notify_channels");

    public List<Profile> loadAll(Path workspace) {
        Path agents = workspace.resolve("agents");
        if (!Files.isDirectory(agents, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<Profile> result = new ArrayList<>();
        try {
            Path workspaceRoot = workspace.toRealPath();
            Path agentsRoot = agents.toRealPath();
            if (!isWithinRealBoundary(workspaceRoot, agentsRoot)) {
                throw new IllegalStateException("Agent 目录越出工作区边界: " + agents);
            }
            try (var directories = Files.list(agentsRoot)) {
                directories.filter(Files::isDirectory).sorted()
                        .forEach(directory -> load(directory, agentsRoot).ifPresent(result::add));
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("无法扫描 Agent 目录: " + agents.toAbsolutePath().normalize(), exception);
        }
        return List.copyOf(result);
    }

    private java.util.Optional<Profile> load(Path agentDirectory, Path agentsRoot) {
        Path source = agentDirectory.resolve("AGENT.md").toAbsolutePath().normalize();
        String agentName = Objects.requireNonNull(agentDirectory.getFileName(),
                "Agent 目录必须具有名称").toString();
        try {
            require(Files.isDirectory(agentDirectory, LinkOption.NOFOLLOW_LINKS), "sourcePath",
                    "Agent 目录不得是符号链接");
            Path realAgentDirectory = agentDirectory.toRealPath();
            require(isWithinRealBoundary(agentsRoot, realAgentDirectory)
                            && agentsRoot.equals(realAgentDirectory.getParent()),
                    "sourcePath", "Agent 目录越出工作区边界");
            require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS), "sourcePath",
                    "AGENT.md 必须是普通文件且不得是符号链接");
            source = source.toRealPath();
            require(isWithinRealBoundary(realAgentDirectory, source)
                            && realAgentDirectory.equals(source.getParent()),
                    "sourcePath", "AGENT.md 越出 Agent 目录边界");
            Map<String, Object> root = parseFrontmatter(source);
            for (String field : LEGACY_FIELDS) {
                require(!root.containsKey(field), field, "已废弃字段不允许出现在 frontmatter");
            }
            String name = string(root, "name");
            require(agentName.equals(name), "name", "必须与目录名 " + agentName + " 完全相等");
            String description = string(root, "description");
            Map<String, Object> identityMap = map(root, "identity");
            Profile.Identity identity = new Profile.Identity(
                    string(identityMap, "agent_name"), string(identityMap, "prompt"));
            Map<String, Object> providerMap = map(root, "provider");
            Double temperature = optionalDouble(providerMap.get("temperature"), "provider.temperature");
            require(temperature == null || temperature >= 0.0 && temperature <= 2.0,
                    "provider.temperature", "范围必须为 0 到 2");
            Profile.ProviderRef provider = new Profile.ProviderRef(
                    string(providerMap, "name"), string(providerMap, "model"), temperature);
            return java.util.Optional.of(new Profile(name, description, identity, provider,
                    strings(root.get("tools"), "tools"), strings(root.get("mcp_servers"), "mcp_servers"),
                    channels(root.get("channels")), schedules(root.get("schedules")),
                    strings(root.get("bootstrap"), "bootstrap"), settings(root.get("settings")), source));
        } catch (RuntimeException | IOException exception) {
            LOGGER.error("Agent 加载失败 agentName={} sourcePath={} field={} reason={}",
                    agentName, source, fieldOf(exception), safeMessage(exception));
            return java.util.Optional.empty();
        }
    }

    private static Map<String, Object> parseFrontmatter(Path source) throws IOException {
        String content = Files.readString(source);
        if (!content.isEmpty() && content.charAt(0) == 0xFEFF) {
            content = content.substring(1);
        }
        String[] lines = content.split("\\R", -1);
        require(lines.length >= 3 && "---".equals(lines[0].trim()), "frontmatter", "缺少起始边界");
        int end = -1;
        for (int index = 1; index < lines.length; index++) {
            if ("---".equals(lines[index].trim())) {
                end = index;
                break;
            }
        }
        require(end > 0, "frontmatter", "缺少结束边界");
        String yamlText = String.join(System.lineSeparator(), java.util.Arrays.copyOfRange(lines, 1, end));
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(50);
        options.setCodePointLimit(3_145_728);
        Object loaded = new Yaml(new SafeConstructor(options)).load(yamlText);
        require(loaded instanceof Map<?, ?>, "frontmatter", "必须是 YAML 对象");
        return castMap((Map<?, ?>) loaded, "frontmatter");
    }

    private static List<Profile.ChannelRef> channels(Object value) {
        List<Profile.ChannelRef> result = new ArrayList<>();
        for (Object item : list(value, "channels")) {
            require(item instanceof Map<?, ?>, "channels", "每项必须是对象");
            Map<String, Object> map = castMap((Map<?, ?>) item, "channels");
            Object config = map.get("config");
            result.add(new Profile.ChannelRef(string(map, "name"),
                    config == null ? Map.of() : castMap(requiredMap(config, "channels.config"), "channels.config")));
        }
        return result;
    }

    private static List<Profile.ScheduleRef> schedules(Object value) {
        List<Profile.ScheduleRef> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (Object item : list(value, "schedules")) {
            require(item instanceof Map<?, ?>, "schedules", "每项必须是对象");
            Map<String, Object> map = castMap((Map<?, ?>) item, "schedules");
            String key = string(map, "key");
            require(keys.add(key), "schedules.key", "同一 Agent 内必须唯一");
            result.add(new Profile.ScheduleRef(key, string(map, "cron"), string(map, "zone"), string(map, "message")));
        }
        return result;
    }

    private static Profile.Settings settings(Object value) {
        if (value == null) {
            return new Profile.Settings(10, 20);
        }
        Map<String, Object> map = castMap(requiredMap(value, "settings"), "settings");
        int iterations = integer(map.getOrDefault("max_iterations", 10), "settings.max_iterations");
        int history = integer(map.getOrDefault("max_history_turns", 20), "settings.max_history_turns");
        require(iterations > 0, "settings.max_iterations", "必须为正整数");
        require(history > 0, "settings.max_history_turns", "必须为正整数");
        return new Profile.Settings(iterations, history);
    }

    private static List<String> strings(Object value, String field) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list(value, field)) {
            require(item instanceof String && !((String) item).isBlank(), field, "必须是非空字符串列表");
            result.add((String) item);
        }
        return List.copyOf(result);
    }

    private static List<?> list(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        require(value instanceof List<?>, field, "必须是列表");
        return (List<?>) value;
    }

    private static Map<?, ?> requiredMap(Object value, String field) {
        require(value instanceof Map<?, ?>, field, "必须是对象");
        return (Map<?, ?>) value;
    }

    private static Map<String, Object> map(Map<String, Object> parent, String field) {
        return castMap(requiredMap(parent.get(field), field), field);
    }

    private static Map<String, Object> castMap(Map<?, ?> source, String field) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            require(key instanceof String, field, "键必须是字符串");
            result.put((String) key, value);
        });
        return result;
    }

    private static String string(Map<String, Object> map, String field) {
        Object value = map.get(field);
        require(value instanceof String && !((String) value).isBlank(), field, "必须是非空字符串");
        return (String) value;
    }

    private static int integer(Object value, String field) {
        require(value instanceof Number, field, "必须是整数");
        try {
            return new BigDecimal(value.toString()).intValueExact();
        }
        catch (ArithmeticException | NumberFormatException exception) {
            throw new ProfileValidationException(field, "必须是 int 范围内的整数");
        }
    }

    static boolean isWithinRealBoundary(Path boundary, Path candidate) {
        return candidate.normalize().startsWith(boundary.normalize());
    }

    private static Double optionalDouble(Object value, String field) {
        if (value == null) {
            return null;
        }
        require(value instanceof Number, field, "必须是数字");
        return ((Number) value).doubleValue();
    }

    private static void require(boolean condition, String field, String message) {
        if (!condition) {
            throw new ProfileValidationException(field, message);
        }
    }

    private static String fieldOf(Exception exception) {
        return exception instanceof ProfileValidationException validation ? validation.field : "frontmatter";
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null
                ? exception.getClass().getSimpleName()
                : message.replaceAll("(?i)(api[-_]?key\\s*[:=]\\s*)\\S+", "$1***");
    }

    private static final class ProfileValidationException extends IllegalArgumentException {

        private final String field;

        private ProfileValidationException(String field, String message) {
            super(message);
            this.field = field;
        }
    }
}
