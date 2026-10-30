package io.oryxos.tool.sandbox;

import io.oryxos.core.context.SkillBinding;
import io.oryxos.core.context.SkillBindingResolver;
import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class WhitelistSandbox implements Sandbox {

    private final Set<String> allowedDomains;

    private final Map<String, Path> allowedCommands;

    private final SkillBindingResolver skillBindings;

    public WhitelistSandbox(Set<String> allowedDomains) {
        this(allowedDomains, Map.of(), null);
    }

    public WhitelistSandbox(Set<String> allowedDomains, Map<String, Path> allowedCommands,
            SkillBindingResolver skillBindings) {
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
                    throw new IllegalArgumentException("Shell executable 必须是绝对普通文件: " + alias);
                }
                commands.put(alias, executable.toRealPath());
            }
            catch (IOException exception) {
                throw new IllegalArgumentException("Shell executable 不可访问: " + alias, exception);
            }
        });
        this.allowedCommands = Map.copyOf(commands);
        this.skillBindings = skillBindings;
    }

    @Override
    public URI enforce(HttpRequestAction action) {
        URI uri = normalize(action.target());
        String host = uri.getHost();
        boolean allowed = allowedDomains.stream().anyMatch(rule -> matches(rule, host));
        if (!allowed) {
            throw new SecurityException("目标地址不在 HTTP 白名单中");
        }
        return uri;
    }

    @Override
    public Path enforce(ShellExecuteAction action) {
        Path executable = allowedCommands.get(action.executableAlias());
        if (executable == null) {
            throw new SecurityException("Shell executable 不在白名单中");
        }
        return executable;
    }

    @Override
    public Path enforce(FileAccessAction action) {
        if (skillBindings == null) {
            throw new SecurityException("文件 Sandbox 未配置 Skill 绑定解析器");
        }
        Path requested = action.requested();
        if (requested.toString().isBlank() || hasTraversal(requested)) {
            throw new SecurityException("文件路径非法");
        }
        try {
            java.util.List<SkillBinding> bindings = skillBindings.resolve(action.profile());
            Path sourcePath = action.profile().sourcePath();
            Path parent = sourcePath.getParent();
            if (parent == null) {
                throw new SecurityException("Agent 定义缺少父目录");
            }
            Path agent = parent.toRealPath();
            Path candidate = requested.isAbsolute() ? requested.normalize() : agent.resolve(requested).normalize();
            if (action.mode() == FileAccessMode.WRITE) {
                return approveWrite(candidate, agent);
            }
            if (Files.notExists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new SecurityException("文件目标不存在");
            }
            Path real = candidate.toRealPath();
            if (within(real, agent)) {
                rejectLinks(candidate, agent);
                return real;
            }
            if (action.mode() == FileAccessMode.WORKING_DIRECTORY) {
                throw new SecurityException("Shell 工作目录只允许当前 Agent 目录");
            }
            for (SkillBinding binding : bindings) {
                if (within(real, binding.directory())) {
                    rejectLinks(candidate, binding.directory());
                    return real;
                }
            }
            throw new SecurityException("文件目标不在当前 Agent 可见边界内");
        }
        catch (IOException exception) {
            throw new SecurityException("文件路径不可访问", exception);
        }
    }

    private static Path approveWrite(Path candidate, Path agent) throws IOException {
        Path existing = candidate;
        while (existing != null && Files.notExists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new SecurityException("文件目标缺少可验证父目录");
        }
        Path realParent = existing.toRealPath();
        if (!within(realParent, agent)) {
            throw new SecurityException("文件写入只允许当前 Agent 目录");
        }
        rejectLinks(existing, agent);
        Path suffix = existing.relativize(candidate);
        Path approved = realParent.resolve(suffix).normalize();
        if (!within(approved, agent)) {
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

    private static void rejectLinks(Path candidate, Path boundary) {
        Path current = candidate;
        while (current != null && !current.equals(boundary)) {
            if (Files.isSymbolicLink(current)) {
                throw new SecurityException("文件路径包含原生链接");
            }
            current = current.getParent();
        }
        if (current == null) {
            throw new SecurityException("文件路径不属于已批准边界");
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
            return new URI(scheme.toLowerCase(Locale.ROOT), null, host, input.getPort(),
                    input.getPath(), input.getQuery(), null);
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
