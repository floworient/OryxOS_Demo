package io.oryxos.core.notify;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class NotifyChannelValidator {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final Pattern ENV = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*}");

    private static final Pattern CONFIG_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final Set<String> DEVICES = Set.of("CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private NotifyChannelValidator() {
    }

    public static void validate(String name, String type, String url, String description,
            Map<String, String> config) {
        validateName(name);
        if (!"webhook".equals(type)) {
            throw new IllegalArgumentException("通知渠道类型当前仅支持 webhook");
        }
        validateUrl(url);
        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("通知渠道描述不能超过 500 字符");
        }
        validateConfig(config);
    }

    public static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("通知渠道名称格式非法");
        }
        String base = name.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (DEVICES.contains(base)) {
            throw new IllegalArgumentException("通知渠道名称不能使用系统设备名");
        }
    }

    public static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("通知渠道 URL 不能为空");
        }
        if (ENV.matcher(url).matches()) {
            return;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("通知渠道 URL 必须是安全的绝对 HTTP(S) 地址");
            }
        }
        catch (URISyntaxException exception) {
            throw new IllegalArgumentException("通知渠道 URL 格式非法", exception);
        }
    }

    private static void validateConfig(Map<String, String> config) {
        if (config == null) {
            throw new IllegalArgumentException("通知渠道 config 不能为空");
        }
        if (config.size() > 32) {
            throw new IllegalArgumentException("通知渠道 config 不能超过 32 项");
        }
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || !CONFIG_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("通知渠道 config 键格式非法");
            }
            if ("url".equalsIgnoreCase(key)) {
                throw new IllegalArgumentException("通知渠道 config 不得覆盖 url");
            }
            if (value == null || value.length() > 4096) {
                throw new IllegalArgumentException("通知渠道 config 值非法");
            }
        }
    }
}
