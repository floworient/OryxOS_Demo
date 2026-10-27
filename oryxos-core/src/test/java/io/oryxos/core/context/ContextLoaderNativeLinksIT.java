package io.oryxos.core.context;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.profile.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** 原生文件系统攻击的附加验证；普通 .link 绑定验收由 ContextLoaderTest 完整执行。 */
@EnabledIfSystemProperty(named = "oryxos.tests.native-links", matches = "true")
class ContextLoaderNativeLinksIT {

    @TempDir
    Path workspace;

    @Test
    void legacyNativeBindingsAndSkillFileEscapesAreRejected() throws Exception {
        var agent = Files.createDirectories(workspace.resolve("agents/weather"));
        var source = agent.resolve("AGENT.md");
        Files.writeString(source, "---\nname: weather\n---\n正文");
        var profile = new Profile("weather", "说明", new Profile.Identity("助手", "身份"),
                new Profile.ProviderRef("p", "m", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(10, 20), source);
        var target = Files.createDirectories(workspace.resolve("skills/news"));
        var bindings = Files.createDirectories(agent.resolve("skills"));
        var link = Files.createSymbolicLink(bindings.resolve("daily.link"), bindings.relativize(target));
        var loader = new ContextLoader(workspace);
        assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("迁移");
        Files.delete(link);
        Files.writeString(bindings.resolve("daily.link"), "../../../skills/news");
        var outside = Files.createDirectories(workspace.resolve("outside")).resolve("SKILL.md");
        Files.writeString(outside, "---\nname: secret\ndescription: 秘密哨兵\n---\n");
        Files.createSymbolicLink(target.resolve("SKILL.md"), target.relativize(outside));
        assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("秘密哨兵");
    }
}
