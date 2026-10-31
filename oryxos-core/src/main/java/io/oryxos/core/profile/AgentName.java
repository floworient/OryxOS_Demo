package io.oryxos.core.profile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 跨入口复用的 Agent 名称安全契约。 */
public final class AgentName {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final Set<String> WINDOWS_DEVICES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private AgentName() {
    }

    public static String requireValid(String value) {
        if (value == null || !VALID_NAME.matcher(value).matches() || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Agent 名称非法: " + value);
        }
        String base = value.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (WINDOWS_DEVICES.contains(base)) {
            throw new IllegalArgumentException("Agent 名称是系统保留名: " + value);
        }
        return value;
    }
}
