package io.oryxos.tool.mcp;

import io.oryxos.tool.sandbox.HttpRequestAction;
import io.oryxos.tool.sandbox.Sandbox;
import io.oryxos.tool.sandbox.ShellExecuteAction;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class McpConfigLoader {

    private static final Pattern ENVIRONMENT = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private static final Set<String> COMMON = Set.of("name", "transport", "request_timeout");

    private final Path config;

    private final Sandbox sandbox;

    private final Function<String, String> environment;

    public McpConfigLoader(Path workspace, Sandbox sandbox, Function<String, String> environment) {
        this.config = workspace.toAbsolutePath().normalize().resolve("mcp_servers.yaml");
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.environment = java.util.Objects.requireNonNull(environment);
    }

    public List<McpServerDefinition> load() {
        if (Files.notExists(config)) {
            return List.of();
        }
        try {
            if (!Files.isRegularFile(config) || Files.isSymbolicLink(config) || Files.size(config) > 1024 * 1024) {
                throw new IllegalArgumentException("配置文件边界非法");
            }
            Object decoded = yaml().load(Files.readString(config));
            if (decoded == null) {
                return List.of();
            }
            if (!(decoded instanceof Map<?, ?> root) || !Set.of("servers").containsAll(root.keySet())) {
                throw new IllegalArgumentException("根字段非法");
            }
            Object rawServers = root.get("servers");
            if (rawServers == null) {
                return List.of();
            }
            if (!(rawServers instanceof List<?> servers)) {
                throw new IllegalArgumentException("servers 必须是列表");
            }
            var names = new LinkedHashSet<String>();
            var definitions = new ArrayList<McpServerDefinition>();
            for (Object raw : servers) {
                if (!(raw instanceof Map<?, ?> server)) {
                    throw new IllegalArgumentException("server 必须是对象");
                }
                String name = string(server, "name", false);
                if (!names.add(name)) {
                    throw new IllegalArgumentException("server name 重复");
                }
                String transport = string(server, "transport", false).toLowerCase(java.util.Locale.ROOT);
                int timeout = integer(server.get("request_timeout"), 30);
                if (timeout < 1 || timeout > 300) {
                    throw new IllegalArgumentException("request_timeout 越界");
                }
                definitions.add(switch (transport) {
                    case "stdio" -> stdio(name, timeout, server);
                    case "sse" -> sse(name, timeout, server);
                    default -> throw new IllegalArgumentException("transport 不支持");
                });
            }
            return List.copyOf(definitions);
        }
        catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalStateException("MCP 配置非法", exception);
        }
    }

    private McpServerDefinition stdio(String name, int timeout, Map<?, ?> server) {
        requireKeys(server, union(COMMON, Set.of("command", "args", "env")));
        Path command = sandbox.enforce(new ShellExecuteAction(string(server, "command", false)));
        List<String> arguments = strings(server.get("args"));
        Map<String, String> env = stringMap(server.get("env"), true);
        env.forEach((key, value) -> {
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*") || value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("MCP env 非法");
            }
        });
        return new McpServerDefinition(name, McpTransport.STDIO, Duration.ofSeconds(timeout), command,
                arguments, env, null, Map.of());
    }

    private McpServerDefinition sse(String name, int timeout, Map<?, ?> server) {
        requireKeys(server, union(COMMON, Set.of("url", "headers")));
        URI url = sandbox.enforce(new HttpRequestAction(URI.create(string(server, "url", false))));
        Map<String, String> headers = stringMap(server.get("headers"), true);
        var headerNames = new java.util.HashSet<String>();
        headers.forEach((key, value) -> {
            String normalized = key.toLowerCase(java.util.Locale.ROOT);
            if (!key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || key.length() > 128
                    || value.length() > 8192 || value.chars().anyMatch(c -> c < 32 || c == 127)
                    || !headerNames.add(normalized)
                    || Set.of("host", "connection", "content-length", "expect", "upgrade",
                            "transfer-encoding").contains(normalized)) {
                throw new IllegalArgumentException("MCP header 非法");
            }
        });
        return new McpServerDefinition(name, McpTransport.SSE, Duration.ofSeconds(timeout), null,
                List.of(), Map.of(), url, headers);
    }

    private Map<String, String> stringMap(Object raw, boolean resolve) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> values) || values.size() > 128) {
            throw new IllegalArgumentException("map 字段非法");
        }
        var result = new LinkedHashMap<String, String>();
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()
                    || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException("map 键值非法");
            }
            result.put(key, resolve ? resolve(value) : value);
        }
        return Map.copyOf(result);
    }

    private String resolve(String value) {
        Matcher matcher = ENVIRONMENT.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String replacement = environment.apply(matcher.group(1));
            if (replacement == null || replacement.isBlank()) {
                throw new IllegalArgumentException("环境变量缺失");
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static List<String> strings(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values) || values.size() > 256) {
            throw new IllegalArgumentException("args 非法");
        }
        var result = new ArrayList<String>();
        for (Object value : values) {
            if (!(value instanceof String text) || text.length() > 8192 || text.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("arg 非法");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static String string(Map<?, ?> values, String field, boolean allowBlank) {
        Object raw = values.get(field);
        if (!(raw instanceof String value) || (!allowBlank && value.isBlank()) || value.length() > 8192) {
            throw new IllegalArgumentException("字符串字段非法");
        }
        return value;
    }

    private static int integer(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("整数字段非法");
        }
        return number.intValue();
    }

    private static void requireKeys(Map<?, ?> values, Set<String> allowed) {
        if (!allowed.containsAll(values.keySet())) {
            throw new IllegalArgumentException("server 含未知字段");
        }
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        var values = new LinkedHashSet<>(first);
        values.addAll(second);
        return Set.copyOf(values);
    }

    private static Yaml yaml() {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(10);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(1024 * 1024);
        return new Yaml(new SafeConstructor(options));
    }
}
