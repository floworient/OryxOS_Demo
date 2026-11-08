package io.oryxos.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.profile.Profile;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class WhitelistSandboxTest {

    @TempDir
    Path workspace;

    @Test
    void deniedActionsHaveTypedSafeReasonsWithoutTargetsOrCauses() throws Exception {
        Profile profile = profile();
        Sandbox sandbox = new WhitelistSandbox(Set.of(), Map.of(), new SkillBindingResolver(workspace));
        assertThatThrownBy(() -> sandbox.enforce(http("https://private.example/?token=canary")))
                .isInstanceOf(SandboxViolationException.class).isInstanceOf(SecurityException.class)
                .hasMessage("HTTP 请求被 Sandbox 拒绝").hasNoCause();
        assertThatThrownBy(() -> sandbox.enforce(new ShellExecuteAction("canary")))
                .isInstanceOf(SandboxViolationException.class)
                .hasMessage("Shell 命令被 Sandbox 拒绝").hasNoCause();
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile,
                workspace.resolve("private-canary"), FileAccessMode.READ)))
                .isInstanceOf(SandboxViolationException.class)
                .hasMessage("文件访问被 Sandbox 拒绝").hasNoCause();
        Sandbox httpOnly = action -> action.target();
        assertThatThrownBy(() -> httpOnly.enforce(new ShellExecuteAction("x")))
                .isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> httpOnly.enforce(new FileAccessAction(profile,
                Path.of("x"), FileAccessMode.READ))).isInstanceOf(SandboxViolationException.class);
    }

    @Test
    void extraRootsAreExplicitAndNeverOverridePublicSkillsOrWorkingDirectory() throws Exception {
        Profile profile = profile();
        Path business = Files.createDirectories(workspace.resolve("business"));
        Path data = Files.writeString(business.resolve("data.txt"), "business");
        Path sibling = Files.createDirectories(workspace.resolve("business-other"));
        Path hidden = Files.writeString(sibling.resolve("data.txt"), "private");
        Path skill = Files.createDirectories(workspace.resolve("skills/news"));
        Path definition = Files.writeString(skill.resolve("SKILL.md"),
                "---\nname: news\ndescription: 新闻\n---\n正文");
        Path unbound = Files.writeString(Files.createDirectories(workspace.resolve("skills/private"))
                .resolve("secret.txt"), "private");
        Path bindings = Files.createDirectories(profile.sourcePath().getParent().resolve("skills"));
        Files.writeString(bindings.resolve("news.link"), "../../../skills/news");
        Sandbox sandbox = extraRoots(List.of(business));
        for (FileAccessMode mode : List.of(FileAccessMode.READ, FileAccessMode.WRITE)) {
            assertThat(sandbox.enforce(new FileAccessAction(profile, data, mode))).isEqualTo(data.toRealPath());
        }
        assertThat(sandbox.enforce(new FileAccessAction(profile, business.resolve("new.txt"),
                FileAccessMode.WRITE))).isEqualTo(business.resolve("new.txt").toAbsolutePath().normalize());
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile, hidden, FileAccessMode.READ)))
                .isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile,
                business.resolve("../business-other/data.txt"), FileAccessMode.READ)))
                .isInstanceOf(SandboxViolationException.class);
        assertThat(sandbox.enforce(new FileAccessAction(profile, definition, FileAccessMode.READ)))
                .isEqualTo(definition.toRealPath());
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile, unbound, FileAccessMode.READ)))
                .isInstanceOf(SandboxViolationException.class);
    }

    @Test
    void broadRootCannotGrantUnboundSkillsWritesOrShellWorkingDirectories() throws Exception {
        Profile profile = profile();
        Path business = Files.createDirectories(workspace.resolve("business"));
        Path bound = Files.createDirectories(workspace.resolve("skills/news"));
        Path definition = Files.writeString(bound.resolve("SKILL.md"),
                "---\nname: news\ndescription: 新闻\n---\n正文");
        Path unbound = Files.writeString(Files.createDirectories(workspace.resolve("skills/private"))
                .resolve("secret.txt"), "private");
        Path bindings = Files.createDirectories(profile.sourcePath().getParent().resolve("skills"));
        Files.writeString(bindings.resolve("news.link"), "../../../skills/news");
        Sandbox sandbox = extraRoots(List.of(workspace));
        assertThat(sandbox.enforce(new FileAccessAction(profile, definition, FileAccessMode.READ)))
                .isEqualTo(definition.toRealPath());
        for (Path denied : List.of(definition, unbound, workspace.resolve("skills/new/file.txt"))) {
            assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile, denied, FileAccessMode.WRITE)))
                    .isInstanceOf(SandboxViolationException.class);
        }
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile, unbound, FileAccessMode.READ)))
                .isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile, business,
                FileAccessMode.WORKING_DIRECTORY))).isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile, workspace.resolve("skills"),
                FileAccessMode.LIST))).isInstanceOf(SandboxViolationException.class);
    }

    @Test
    void rootConfigurationIsValidatedAndCopiedWithoutLeakingValues() throws Exception {
        Profile profile = profile();
        Path business = Files.createDirectories(workspace.resolve("business"));
        Path file = Files.writeString(business.resolve("data.txt"), "data");
        var roots = new java.util.ArrayList<>(List.of(business, business));
        Sandbox sandbox = extraRoots(roots);
        roots.clear();
        assertThat(sandbox.enforce(new FileAccessAction(profile, file, FileAccessMode.READ)))
                .isEqualTo(file.toRealPath());
        for (Path invalid : List.of(Path.of("relative-canary"), Path.of(""),
                workspace.resolve("missing-canary"), file)) {
            assertThatThrownBy(() -> extraRoots(List.of(invalid))).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("文件白名单配置非法").hasNoCause();
        }
        assertThatThrownBy(() -> extraRoots(java.util.Arrays.asList((Path) null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("文件白名单配置非法");
    }

    @Test
    void missingPublicRootAndBrokenBindingsFailClosed() throws Exception {
        Profile profile = profile();
        Sandbox sandbox = extraRoots(List.of(workspace));
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile,
                workspace.resolve("skills/new.txt"), FileAccessMode.WRITE)))
                .isInstanceOf(SandboxViolationException.class);
        Path bindings = Files.createDirectories(profile.sourcePath().getParent().resolve("skills"));
        Files.writeString(bindings.resolve("bad.link"), "../../../skills/missing");
        assertThatThrownBy(() -> sandbox.enforce(new FileAccessAction(profile,
                profile.sourcePath(), FileAccessMode.READ)))
                .isInstanceOf(SandboxViolationException.class).hasNoCause();
    }

    @Test
    void linkedRootOrLinkedTargetCannotExpandFileBoundary() throws Exception {
        Profile profile = profile();
        Path business = Files.createDirectories(workspace.resolve("business"));
        Path outside = Files.writeString(workspace.resolve("outside.txt"), "secret");
        Path directoryLink = workspace.resolve("linked-business");
        try {
            Files.createSymbolicLink(directoryLink, business);
            Files.createSymbolicLink(business.resolve("escape.txt"), outside);
        }
        catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "当前平台不能创建原生链接，需单列未验证范围");
        }
        assertThatThrownBy(() -> extraRoots(List.of(directoryLink)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extraRoots(List.of(business)).enforce(new FileAccessAction(profile,
                business.resolve("escape.txt"), FileAccessMode.READ)))
                .isInstanceOf(SandboxViolationException.class);
    }

    private Sandbox extraRoots(List<Path> roots) {
        return new WhitelistSandbox(Set.of(), Map.of(), new SkillBindingResolver(workspace),
                roots, workspace.resolve("skills"));
    }

    @Test
    void allowlistSummaryUsesEffectiveNormalizedRules() {
        var summary = new WhitelistSandbox(Set.of("Z.example", "a.example", "z.example"))
                .allowlistSummary();
        assertThat(summary.httpDomains()).containsExactly("a.example", "z.example");
        assertThat(summary.fileRootCount()).isZero();
        assertThat(summary.commandAliases()).isEmpty();
    }

    @Test
    void normalizingHostPreservesEncodedPathAndQuerySemantics() {
        Sandbox sandbox = new WhitelistSandbox(Set.of("example.com"));
        for (String suffix : List.of("/a%2Fb?q=a%26b%3Dc", "/%252F?q=%2B+%25%2f",
                "/%E4%B8%AD?q=%E6%96%87", "/path?", "?q=x%3Fy%23z")) {
            URI approved = sandbox.enforce(http("HTTPS://EXAMPLE.COM:8443" + suffix));
            assertThat(approved.toASCIIString()).isEqualTo("https://example.com:8443" + suffix);
        }
    }

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
