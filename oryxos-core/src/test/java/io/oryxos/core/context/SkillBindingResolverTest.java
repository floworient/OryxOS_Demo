package io.oryxos.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.profile.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillBindingResolverTest {

    @TempDir
    Path workspace;

    @Test
    void resolvesValidatedMetadataAndReloadsLinkOnEveryCall() throws Exception {
        var profile = profile();
        var news = skill("news", "新闻摘要");
        var weather = skill("weather", "天气摘要");
        Path link = bind(profile, "daily", "../../../skills/news");
        var resolver = new SkillBindingResolver(workspace);

        assertThat(resolver.resolve(profile)).singleElement().satisfies(binding -> {
            assertThat(binding.name()).isEqualTo("news");
            assertThat(binding.description()).isEqualTo("新闻摘要");
            assertThat(binding.directory()).isEqualTo(news.toRealPath());
            assertThat(binding.definitionFile()).isEqualTo(news.resolve("SKILL.md").toRealPath());
        });

        Files.writeString(link, "../../../skills/weather");
        assertThat(resolver.resolve(profile)).singleElement().satisfies(binding ->
                assertThat(binding.directory()).isEqualTo(weather.toRealPath()));
    }

    @Test
    void rejectsNativeLinksAbsoluteTraversalBrokenAndNonSkillTargets() throws Exception {
        var profile = profile();
        skill("news", "新闻摘要");
        Path link = bind(profile, "bad", "../../../outside");
        var resolver = new SkillBindingResolver(workspace);
        assertThatThrownBy(() -> resolver.resolve(profile)).isInstanceOf(IllegalStateException.class);

        for (String invalid : List.of("C:\\outside", "/outside", "../../../skills/missing")) {
            Files.writeString(link, invalid);
            assertThatThrownBy(() -> resolver.resolve(profile)).isInstanceOf(IllegalStateException.class);
        }
    }

    private Profile profile() throws Exception {
        Path source = Files.createDirectories(workspace.resolve("agents/weather")).resolve("AGENT.md");
        Files.writeString(source, "---\nname: weather\n---\n正文");
        return new Profile("weather", "说明", new Profile.Identity("助手", "身份"),
                new Profile.ProviderRef("p", "m", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(10, 20), source);
    }

    private Path skill(String name, String description) throws Exception {
        Path directory = Files.createDirectories(workspace.resolve("skills").resolve(name));
        Files.writeString(directory.resolve("SKILL.md"), "---\nname: " + name
                + "\ndescription: " + description + "\n---\n正文");
        return directory;
    }

    private static Path bind(Profile profile, String alias, String target) throws Exception {
        Path directory = Files.createDirectories(profile.sourcePath().getParent().resolve("skills"));
        return Files.writeString(directory.resolve(alias + ".link"), target);
    }
}
