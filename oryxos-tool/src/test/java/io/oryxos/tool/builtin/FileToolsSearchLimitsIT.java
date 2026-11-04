package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.tool.sandbox.WhitelistSandbox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 真实大目录回归由 Failsafe 在独立测试 JVM 中运行，不与短超时 HTTP 单测共享资源。 */
class FileToolsSearchLimitsIT {

    @TempDir
    Path workspace;

    @Test
    void grepKeepsTraversalTruncationWhenOversizedFilesAreSkipped() throws Exception {
        Path agent = Files.createDirectories(workspace.resolve("agents/reader"));
        Path source = Files.writeString(agent.resolve("AGENT.md"), "---\nname: reader\n---\n测试");
        var profile = new Profile("reader", "测试", new Profile.Identity("助手", "测试"),
                new Profile.ProviderRef("stub", "stub", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(3, 10), source);
        ProfileContext.set(profile);
        try {
            Path root = Files.createDirectories(agent.resolve("bounded-search"));
            Path large = Files.write(root.resolve("large-00.txt"), new byte[2 * 1024 * 1024]);
            // 共 10002 个普通文件；最多遗漏三项，任意遍历顺序都能采集超大文件。
            for (int index = 1; index < 10; index++) {
                Files.createLink(root.resolve("large-%02d.txt".formatted(index)), large);
            }
            for (int index = 0; index < 9992; index++) {
                Files.createFile(root.resolve("empty-%05d.txt".formatted(index)));
            }
            var tools = new FileTools(new WhitelistSandbox(Set.of(), Map.of(), new SkillBindingResolver(workspace)));
            var grep = tools.all().stream().filter(tool -> tool.getName().equals("grep")).findFirst().orElseThrow();
            var result = grep.execute("{\"path\":\"bounded-search\",\"pattern\":\"needle\"}");

            assertThat(result.success()).isTrue();
            assertThat(result.content()).contains("\"matches\":[]", "\"truncated\":true");
        }
        finally {
            ProfileContext.clear();
        }
    }
}
