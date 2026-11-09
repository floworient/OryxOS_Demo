package io.oryxos.boot;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;

final class SchedulerAcceptanceConfig {

    private SchedulerAcceptanceConfig() {
    }

    static Path prepare(Path workspace) throws IOException {
        Path local = Path.of("..", ".oryxos", "lesson28-acceptance.yaml").toAbsolutePath().normalize();
        Path configuration = workspace.resolve("application.yaml");
        if (Files.isRegularFile(local) && new YamlPropertySourceLoader().load("acceptance",
                new FileSystemResource(local)).stream().anyMatch(source ->
                        source.getProperty("oryxos.providers[0].api-key") instanceof String key
                                && StringUtils.hasText(key))) {
            Files.writeString(configuration, "spring.config.import: \"" + local.toUri() + "\"\n");
        } else {
            assumeTrue(StringUtils.hasText(System.getenv("ALIYUN_API_KEY")),
                    "请填写 .oryxos/lesson28-acceptance.yaml 中的 api-key");
            Files.writeString(configuration, """
                    oryxos:
                      providers:
                        - name: qwen
                          type: DASHSCOPE
                          model: qwen-plus
                          api-key: ${ALIYUN_API_KEY}
                    """);
        }
        return configuration;
    }
}
