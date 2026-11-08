package io.oryxos.tool.sandbox;

import io.oryxos.core.context.SkillBinding;
import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.tool.SandboxAllowlistReader;
import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class WhitelistSandbox implements Sandbox {

    private final Set<String> allowedDomains;

    private final Map<String, Path> allowedCommands;

    private final SkillBindingResolver skillBindings;

    private final List<Path> allowedFileRoots;

    private final Path publicSkillRoot;

    public WhitelistSandbox(Set<String> allowedDomains) {
        this(allowedDomains, Map.of(), null);
    }

    public WhitelistSandbox(Set<String> allowedDomains, Map<String, Path> allowedCommands,
            SkillBindingResolver skillBindings) {
        this(allowedDomains, allowedCommands, skillBindings, List.of(), null);
    }

    public WhitelistSandbox(Set<String> allowedDomains, Map<String, Path> allowedCommands,
            SkillBindingResolver skillBindings, List<Path> allowedFileRoots, Path publicSkillRoot) {
        this.allowedDomains = Set.copyOf(allowedDomains).stream()
                .map(WhitelistSandbox::normalizeRule)
                .collect(Collectors.toUnmodifiableSet());
        var commands = new LinkedHashMap<String, Path>();
        allowedCommands.forEach((alias, executable) -> {
            if (alias == null || alias.isBlank() || commands.containsKey(alias)) {
                throw new IllegalArgumentException("Shell executable alias 非法或重复");
            }
            try {
                if (!executable.isAbsolute() || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(executable)) {
                    throw new IllegalArgumentException("Shell executable 配置非法");
                }
                commands.put(alias, executable.toRealPath());
            }
            catch (IOException exception) {
                throw new IllegalArgumentException("Shell executable 配置非法");
            }
        });
        this.allowedCommands = Map.copyOf(commands);
        this.skillBindings = skillBindings;
        this.allowedFileRoots = validateRoots(allowedFileRoots);
        this.publicSkillRoot = publicSkillRoot == null ? null : publicSkillRoot.toAbsolutePath().normalize();
    }

    public SandboxAllowlistReader.Summary allowlistSummary() {
        return new SandboxAllowlistReader.Summary(allowedDomains.stream().sorted().toList(),
                allowedFileRoots.size(), allowedCommands.keySet().stream().sorted().toList());
    }

    @Override
    public URI enforce(HttpRequestAction action) {
        try {
            URI uri = normalize(action.target());
            if (allowedDomains.stream().noneMatch(rule -> matches(rule, uri.getHost()))) {
                throw new SecurityException();
            }
            return uri;
        }
        catch (RuntimeException exception) {
            throw new SandboxViolationException(SandboxViolationException.HTTP_DENIED);
        }
    }

    @Override
    public Path enforce(ShellExecuteAction action) {
        Path executable = allowedCommands.get(action.executableAlias());
        if (executable == null) {
            throw new SandboxViolationException(SandboxViolationException.SHELL_DENIED);
        }
        return executable;
    }

    @Override
    public Path enforce(FileAccessAction action) {
        try {
            return approveFile(action);
        }
        catch (IOException | RuntimeException exception) {
            throw new SandboxViolationException(SandboxViolationException.FILE_DENIED);
        }
    }

    private Path approveFile(FileAccessAction action) throws IOException {
        if (skillBindings == null) {
            throw new SecurityException();
        }
        Path requested = action.requested();
        if (requested.toString().isBlank() || hasTraversal(requested)) {
            throw new SecurityException("文件路径非法");
        }
        List<SkillBinding> bindings = skillBindings.resolve(action.profile());
        Path sourcePath = action.profile().sourcePath();
        Path parent = sourcePath.getParent();
        if (parent == null) {
            throw new SecurityException("Agent 定义缺少父目录");
        }
        Path agent = parent.toRealPath();
        Path candidate = requested.isAbsolute() ? requested.normalize() : agent.resolve(requested).normalize();
        Path agentsDirectory = parent.getParent();
        Path workspace = agentsDirectory == null ? null : agentsDirectory.getParent();
        if (workspace == null) {
            throw new SecurityException("Agent 定义缺少工作区边界");
        }
        Path skills = publicSkillRoot == null
                ? workspace.resolve("skills").toAbsolutePath().normalize() : publicSkillRoot;
        if (action.mode() == FileAccessMode.WRITE) {
            return approveWrite(candidate, agent, skills);
        }
        if (Files.notExists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("文件目标不存在");
        }
        Path real = candidate.toRealPath();
        rejectLinks(candidate);
        if (protectedSkill(candidate, real, skills)) {
            if (action.mode() != FileAccessMode.WORKING_DIRECTORY
                    && bindings.stream().anyMatch(binding -> within(real, binding.directory()))) {
                return real;
            }
            throw new SecurityException();
        }
        if (within(real, agent)) {
            return real;
        }
        if (action.mode() == FileAccessMode.WORKING_DIRECTORY) {
            throw new SecurityException("Shell 工作目录只允许当前 Agent 目录");
        }
        if (allowedFileRoots.stream().anyMatch(root -> within(real, root))) {
            return real;
        }
        throw new SecurityException("文件目标不在当前 Agent 可见边界内");
    }

    private Path approveWrite(Path candidate, Path agent, Path skills) throws IOException {
        Path existing = candidate;
        while (existing != null && Files.notExists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new SecurityException("文件目标缺少可验证父目录");
        }
        Path realParent = existing.toRealPath();
        rejectLinks(existing);
        Path suffix = existing.relativize(candidate);
        Path approved = realParent.resolve(suffix).normalize();
        if (protectedSkill(candidate, approved, skills)
                || !(within(approved, agent) || allowedFileRoots.stream().anyMatch(root -> within(approved, root)))) {
            throw new SecurityException("文件写入越界");
        }
        return approved;
    }

    private static boolean within(Path target, Path boundary) {
        return target.equals(boundary) || target.startsWith(boundary);
    }

    private static boolean hasTraversal(Path path) {
        for (Path part : path) {
            if (part.toString().equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static void rejectLinks(Path candidate) throws IOException {
        Path current = candidate;
        while (current != null) {
            if (Files.isSymbolicLink(current)
                    || !current.toRealPath().equals(current.toAbsolutePath().normalize())) {
                throw new SecurityException("文件路径包含原生链接");
            }
            current = current.getParent();
        }
    }

    private static boolean protectedSkill(Path candidate, Path real, Path skills) throws IOException {
        return within(candidate, skills) || (Files.exists(skills) && within(real, skills.toRealPath()));
    }

    private static List<Path> validateRoots(List<Path> roots) {
        try {
            var validated = new java.util.LinkedHashSet<Path>();
            for (Path root : roots) {
                if (root == null || !root.isAbsolute() || hasTraversal(root)
                        || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException();
                }
                rejectLinks(root);
                validated.add(root.toRealPath());
            }
            return List.copyOf(validated);
        }
        catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("文件白名单配置非法");
        }
    }

    private static URI normalize(URI input) {
        String scheme = input.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || !input.isAbsolute() || input.getHost() == null || input.getHost().isBlank()
                || input.getUserInfo() != null || input.getFragment() != null
                || input.getPort() < -1 || input.getPort() > 65_535) {
            throw new SecurityException("目标地址不是安全的绝对 HTTP(S) URI");
        }
        String host = normalizeHost(input.getHost());
        try {
            // 多参数 URI 构造器接受解码值，会把 %2F/%26/%3D 重新解释为分隔符。
            // 仅规范化已校验的 scheme/host；路径和查询保持原始编码与空查询语义。
            String port = input.getPort() == -1 ? "" : ":" + input.getPort();
            String query = input.getRawQuery() == null ? "" : "?" + input.getRawQuery();
            return new URI(scheme.toLowerCase(Locale.ROOT) + "://" + host + port
                    + input.getRawPath() + query);
        }
        catch (URISyntaxException exception) {
            throw new SecurityException("目标地址非法", exception);
        }
    }

    private static boolean matches(String rule, String host) {
        if (rule.startsWith("*.")) {
            String suffix = rule.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(rule);
    }

    private static String normalizeRule(String rule) {
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("HTTP 白名单域名不能为空");
        }
        String value = rule.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("*.")) {
            return "*." + normalizeHost(value.substring(2));
        }
        if (value.contains("*")) {
            throw new IllegalArgumentException("HTTP 白名单通配符格式非法");
        }
        return normalizeHost(value);
    }

    private static String normalizeHost(String host) {
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("域名格式非法", exception);
        }
    }
}
