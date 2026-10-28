package io.oryxos.cli;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** 解析所有 CLI 命令共享的工作区根路径。 */
public final class WorkspaceRootResolver {
    public Path resolve(Properties properties, Map<String, String> environment, Path userDirectory) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(userDirectory, "userDirectory");
        String configured = nonBlank(properties.getProperty("oryxos.root"));
        if (configured == null) {
            configured = nonBlank(environment.get("ORYXOS_ROOT"));
        }
        Path selected = configured == null ? Path.of(".oryxos") : Path.of(configured);
        if (!selected.isAbsolute()) {
            selected = userDirectory.resolve(selected);
        }
        return selected.toAbsolutePath().normalize();
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
