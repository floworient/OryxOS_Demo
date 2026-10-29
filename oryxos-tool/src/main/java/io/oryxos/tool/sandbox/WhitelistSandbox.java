package io.oryxos.tool.sandbox;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class WhitelistSandbox implements Sandbox {

    private final Set<String> allowedDomains;

    public WhitelistSandbox(Set<String> allowedDomains) {
        this.allowedDomains = Set.copyOf(allowedDomains).stream()
                .map(WhitelistSandbox::normalizeRule)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public URI enforce(SandboxAction action) {
        if (action.type() != ActionType.HTTP_REQUEST) {
            throw new SecurityException("Sandbox 不允许该操作");
        }
        URI uri = normalize(action.target());
        String host = uri.getHost();
        boolean allowed = allowedDomains.stream().anyMatch(rule -> matches(rule, host));
        if (!allowed) {
            throw new SecurityException("目标地址不在 HTTP 白名单中");
        }
        return uri;
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
