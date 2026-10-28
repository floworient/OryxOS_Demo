package io.oryxos.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceRootResolverTest {
    @TempDir
    Path userDir;

    @Test
    void systemPropertyOverridesEnvironmentAndRelativePathsResolveAgainstUserDir() {
        var properties = new Properties();
        properties.setProperty("oryxos.root", "configured/../system-root");

        Path result = new WorkspaceRootResolver().resolve(
                properties, Map.of("ORYXOS_ROOT", "environment-root"), userDir);

        assertThat(result).isEqualTo(userDir.resolve("system-root").toAbsolutePath().normalize());
    }

    @Test
    void environmentOverridesDefaultAndBlankValuesFallBack() {
        var resolver = new WorkspaceRootResolver();
        var blank = new Properties();
        blank.setProperty("oryxos.root", "  ");
        assertThat(resolver.resolve(blank, Map.of("ORYXOS_ROOT", "env-root"), userDir))
                .isEqualTo(userDir.resolve("env-root").toAbsolutePath().normalize());

        assertThat(resolver.resolve(new Properties(), Map.of("ORYXOS_ROOT", " "), userDir))
                .isEqualTo(userDir.resolve(".oryxos").toAbsolutePath().normalize());
    }
}
