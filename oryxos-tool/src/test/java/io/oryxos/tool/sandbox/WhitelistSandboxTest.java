package io.oryxos.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.profile.Profile;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WhitelistSandboxTest {

    @TempDir
    Path workspace;

    @Test
    void allowsExactAndLabelBoundedSubdomain() {
        Sandbox sandbox = new WhitelistSandbox(Set.of("example.com", "*.hooks.example.org"));
        assertThat(sandbox.enforce(http("https://EXAMPLE.com/a")).getHost()).isEqualTo("example.com");
        assertThat(sandbox.enforce(http("https://a.hooks.example.org/a")).getHost())
                .isEqualTo("a.hooks.example.org");
    }

    @Test
    void defaultsToDenyAndRejectsUnsafeUris() {
        assertThatThrownBy(() -> new WhitelistSandbox(Set.of()).enforce(http("https://example.com")))
                .isInstanceOf(SecurityException.class);
        Sandbox sandbox = new WhitelistSandbox(Set.of("*.example.com"));
        assertThatThrownBy(() -> sandbox.enforce(http("https://example.com")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(http("https://evil-example.com")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(http("https://u:p@a.example.com")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(http("https://a.example.com/#x")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(http("file:///tmp/x"))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(http("https://a.example.com:99999/x")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void resolvesConfiguredShellAliasToRealAbsoluteExecutable() throws Exception {
        Path executable = Files.writeString(workspace.resolve("tool.exe"), "stub");
        Sandbox sandbox = new WhitelistSandbox(Set.of(), Map.of("safe", executable), null);

        assertThat(sandbox.enforce(new ShellExecuteAction("safe"))).isEqualTo(executable.toRealPath());
        assertThatThrownBy(() -> sandbox.enforce(new ShellExecuteAction("missing")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void agentDirectoryIsWritableAndBoundSkillIsReadOnly() throws Exception {
        Profile profile = profile();
        Path agentFile = Files.writeString(profile.sourcePath().getParent().resolve("notes.md"), "agent");
        Path skill = Files.createDirectories(workspace.resolve("skills/news"));
        Path skillFile = Files.writeString(skill.resolve("SKILL.md"),
                "---\nname: news\ndescription: 新闻\n---\n正文");
        Path bindings = Files.createDirectories(profile.sourcePath().getParent().resolve("skills"));
        Files.writeString(bindings.resolve("daily.link"), "../../../skills/news");
        Sandbox sandbox = new WhitelistSandbox(Set.of(), Map.of(), new SkillBindingResolver(workspace));

        assertThat(sandbox.enforce(new FileAccessAction(profile, agentFile, FileAccessMode.WRITE)))
                .isEqualTo(agentFile.toRealPath());
        assertThat(sandbox.enforce(new FileAccessAction(profile, Path.of("notes.md"), FileAccessMode.READ)))
                .isEqualTo(agentFile.toRealPath());
        Path newFile = profile.sourcePath().getParent().resolve("new/sub/file.md");
        assertThat(sandbox.enforce(new FileAccessAction(profile, Path.of("new/sub/file.md"),
                FileAccessMode.WRITE))).isEqualTo(newFile.toAbsolutePath().normalize());
        assertThat(sandbox.enforce(new FileAccessAction(profile, skillFile, FileAccessMode.READ)))
                .isEqualTo(skillFile.toRealPath());
        assertThatThrownBy(() -> sandbox.enforce(
                new FileAccessAction(profile, skillFile, FileAccessMode.WRITE)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile,
                workspace.resolve("skills/unbound/SKILL.md"), FileAccessMode.READ)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile,
                Path.of("..", "outside.md"), FileAccessMode.WRITE)))
                .isInstanceOf(SecurityException.class);
    }

    private Profile profile() throws Exception {
        Path source = Files.createDirectories(workspace.resolve("agents/weather")).resolve("AGENT.md");
        Files.writeString(source, "---\nname: weather\n---\n正文");
        return new Profile("weather", "说明", new Profile.Identity("助手", "身份"),
                new Profile.ProviderRef("p", "m", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(3, 10), source);
    }

    private static HttpRequestAction http(String uri) {
        return new HttpRequestAction(URI.create(uri));
    }
}
