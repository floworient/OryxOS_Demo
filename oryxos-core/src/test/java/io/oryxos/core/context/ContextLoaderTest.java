package io.oryxos.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.profile.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ContextLoaderTest {

    @TempDir
    Path workspace;

    private Profile profile(Path root, List<String> bootstrap) throws Exception {
        var source = Files.createDirectories(root.resolve("agents/weather")).resolve("AGENT.md");
        Files.writeString(source, "---\nname: weather\n---\n正文一");
        return new Profile("weather", "说明", new Profile.Identity("助手", "身份"),
                new Profile.ProviderRef("p", "m", null), List.of(), List.of(), List.of(), List.of(),
                bootstrap, new Profile.Settings(10, 20), source);
    }

    private Path skill(Path root, String name, String description) throws Exception {
        var source = Files.createDirectories(root.resolve("skills/" + name));
        Files.writeString(source.resolve("SKILL.md"), "---\nname: " + name + "\ndescription: "
                + description + "\n---\n正文哨兵");
        return source;
    }

    private Path binding(Profile profile, String name, String target) throws Exception {
        var bindings = Files.createDirectories(profile.sourcePath().getParent().resolve("skills"));
        var reference = bindings.resolve(name + ".link");
        Files.writeString(reference, target);
        return reference;
    }

    @Test
    void bodyAndBootstrapAreReloadedAndMissingBootstrapWarns(CapturedOutput output) throws Exception {
        var profile = profile(workspace, List.of("SOUL.md", "missing.md"));
        Files.writeString(workspace.resolve("SOUL.md"), "人格一");
        var loader = new ContextLoader(workspace);
        assertThat(loader.load(profile)).contains("正文一", "人格一");
        assertThat(output.getOut() + output.getErr()).contains("missing.md", "weather");
        Files.writeString(profile.sourcePath(), "---\nname: weather\n---\n正文二");
        Files.writeString(workspace.resolve("SOUL.md"), "人格二");
        assertThat(loader.load(profile)).contains("正文二", "人格二").doesNotContain("正文一", "人格一");
    }

    @Test
    void bomPrefixedAgentAndSkillFrontmatterLoad() throws Exception {
        var source = Files.createDirectories(workspace.resolve("agents/weather")).resolve("AGENT.md");
        Files.writeString(source, ((char) 0xFEFF) + "---\nname: weather\n---\n正文一");
        var profile = new Profile("weather", "说明", new Profile.Identity("助手", "身份"),
                new Profile.ProviderRef("p", "m", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(10, 20), source);
        var skillDir = Files.createDirectories(workspace.resolve("skills/news"));
        Files.writeString(skillDir.resolve("SKILL.md"),
                ((char) 0xFEFF) + "---\nname: news\ndescription: 新闻摘要\n---\n正文哨兵");
        binding(profile, "daily", "../../../skills/news");
        var loader = new ContextLoader(workspace);
        assertThat(loader.load(profile)).contains("正文一").contains("新闻摘要");
    }

    @Test
    void projectReferencesLoadOnlyBoundMetadataAndRealReadablePath() throws Exception {
        var profile = profile(workspace, List.of());
        var target = skill(workspace, "news", "新闻摘要");
        skill(workspace, "unbound", "未绑定哨兵");
        var reference = binding(profile, "daily", "../../../skills/news");
        var loader = new ContextLoader(workspace);
        assertThat(loader.load(profile)).contains("news", "新闻摘要", target.resolve("SKILL.md").toRealPath().toString())
                .doesNotContain("正文哨兵", "未绑定哨兵", reference + "/SKILL.md");
        Files.writeString(target.resolve("SKILL.md"), "---\nname: news\ndescription: 新说明\n---\n正文哨兵");
        assertThat(loader.load(profile)).contains("新说明").doesNotContain("新闻摘要");
    }

    @Test
    void editsAndDeletionOfReferenceChangeBindingWithoutRestart() throws Exception {
        var profile = profile(workspace, List.of());
        skill(workspace, "news", "新闻摘要");
        var second = skill(workspace, "weather", "天气摘要");
        var reference = binding(profile, "daily", "../../../skills/news");
        var loader = new ContextLoader(workspace);
        assertThat(loader.load(profile)).contains("新闻摘要").doesNotContain("天气摘要");
        Files.writeString(reference, "../../../skills/weather");
        assertThat(loader.load(profile)).contains("天气摘要", second.resolve("SKILL.md").toRealPath().toString())
                .doesNotContain("新闻摘要");
        Files.delete(reference);
        assertThat(loader.load(profile)).doesNotContain("天气摘要", "新闻摘要", "正文哨兵");
    }

    @Test
    void movingProjectKeepsRelativeReferencesValid() throws Exception {
        var initial = Files.createDirectories(workspace.resolve("original"));
        var profile = profile(initial, List.of());
        skill(initial, "news", "新闻摘要");
        binding(profile, "daily", "../../../skills/news");
        var moved = Files.move(initial, workspace.resolve("relocated"));
        var movedProfile = profile(moved, List.of());
        assertThat(new ContextLoader(moved).load(movedProfile)).contains("新闻摘要",
                moved.resolve("skills/news/SKILL.md").toRealPath().toString()).doesNotContain(initial.toString());
    }

    @Test
    void utf8BomTrailingNewlineAndPortableBackslashesAreAccepted() throws Exception {
        var profile = profile(workspace, List.of());
        skill(workspace, "news", "新闻摘要");
        binding(profile, "daily", "\ufeff..\\..\\..\\skills\\news\r\n");
        assertThat(new ContextLoader(workspace).load(profile)).contains("新闻摘要");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "../../../skills/news\n../../../skills/other", "/tmp/outside",
            "C:\\outside", "C:relative", "\\\\server\\share", "//server/share", "../../../outside",
            "../../..", "../../../skills/missing", "../../../skills/news/SKILL.md"})
    void invalidTargetsAreRejectedWithoutLoadingOutsideContent(String target) throws Exception {
        var profile = profile(workspace, List.of());
        skill(workspace, "news", "新闻摘要");
        var outside = Files.createDirectories(workspace.resolve("outside"));
        Files.writeString(outside.resolve("SKILL.md"), "---\nname: secret\ndescription: 秘密哨兵\n---\n");
        binding(profile, "bad", target);
        assertThatThrownBy(() -> new ContextLoader(workspace).load(profile))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("weather")
                .hasMessageNotContaining("秘密哨兵");
    }

    @Test
    void copiedDirectoriesUnknownFilesAndOversizedReferencesAreRejected() throws Exception {
        var profile = profile(workspace, List.of());
        var reference = binding(profile, "bad", "x".repeat(4097));
        var loader = new ContextLoader(workspace);
        assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class);
        Files.delete(reference);
        var copied = Files.createDirectory(reference);
        assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class);
        Files.delete(copied);
        var unknown = reference.resolveSibling("README.md");
        Files.writeString(unknown, "../../../skills/news");
        assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class);
        Files.delete(unknown);
        Files.writeString(reference.resolveSibling(".link"), "../../../skills/news");
        assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bootstrapTraversalAndMissingOrInvalidSkillMetadataFail() throws Exception {
        var traversing = profile(workspace, List.of("../secret.md"));
        var loader = new ContextLoader(workspace);
        assertThatThrownBy(() -> loader.load(traversing)).isInstanceOf(IllegalStateException.class);
        var profile = profile(workspace, List.of());
        var target = skill(workspace, "news", "新闻摘要");
        binding(profile, "daily", "../../../skills/news");
        Files.delete(target.resolve("SKILL.md"));
        assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class);
        for (String invalid : List.of("---\nname: news\n---\n正文哨兵", "---\nname: []\ndescription: x\n---",
                "---\nname: first\nname: second\ndescription: x\n---", "---\nname: news")) {
            Files.writeString(target.resolve("SKILL.md"), invalid);
            assertThatThrownBy(() -> loader.load(profile)).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("正文哨兵");
        }
    }
}
