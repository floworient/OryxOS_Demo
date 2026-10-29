package io.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.tool.sandbox.WhitelistSandbox;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileToolsTest {

    @TempDir
    Path workspace;

    private Profile profile;

    private FileTools tools;

    @BeforeEach
    void setUp() throws Exception {
        Path source = Files.createDirectories(workspace.resolve("agents/weather")).resolve("AGENT.md");
        Files.writeString(source, "---\nname: weather\n---\n正文");
        profile = new Profile("weather", "说明", new Profile.Identity("助手", "身份"),
                new Profile.ProviderRef("p", "m", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), new Profile.Settings(3, 10), source);
        ProfileContext.set(profile);
        var sandbox = new WhitelistSandbox(Set.of(), Map.of(), new SkillBindingResolver(workspace));
        tools = new FileTools(sandbox);
    }

    @AfterEach
    void clear() {
        ProfileContext.clear();
    }

    @Test
    void readsWritesAndListsAgentFilesWithStableStructuredOutput() throws Exception {
        Path agent = profile.sourcePath().getParent();
        Files.writeString(agent.resolve("b.txt"), "乙");
        Files.writeString(agent.resolve("a.txt"), "甲");

        assertThat(call("read_file", "{\"path\":\"a.txt\"}").content()).isEqualTo("甲");
        assertThat(call("write_file", "{\"path\":\"a.txt\",\"content\":\"新内容\"}").success())
                .isTrue();
        assertThat(Files.readString(agent.resolve("a.txt"))).isEqualTo("新内容");
        assertThat(call("list_dir", "{\"path\":\".\"}").content())
                .contains("a.txt", "b.txt", "AGENT.md")
                .satisfies(content -> assertThat(content.indexOf("a.txt")).isLessThan(content.indexOf("b.txt")));
    }

    @Test
    void boundSkillCanBeReadButNeverWrittenAndUnboundAbsolutePathIsRejected() throws Exception {
        Path skill = Files.createDirectories(workspace.resolve("skills/news"));
        Path definition = Files.writeString(skill.resolve("SKILL.md"),
                "---\nname: news\ndescription: 新闻\n---\n技能正文");
        Path bindings = Files.createDirectories(profile.sourcePath().getParent().resolve("skills"));
        Files.writeString(bindings.resolve("daily.link"), "../../../skills/news");
        Path unbound = Files.createDirectories(workspace.resolve("skills/private"));
        Path secret = Files.writeString(unbound.resolve("SKILL.md"), "秘密");

        assertThat(call("read_file", jsonPath(definition)).content()).contains("技能正文");
        assertThat(call("write_file", "{\"path\":\"" + escaped(definition)
                + "\",\"content\":\"篡改\"}").success()).isFalse();
        assertThat(Files.readString(definition)).contains("技能正文");
        assertThat(call("read_file", jsonPath(secret)).success()).isFalse();
    }

    @Test
    void editRequiresExactlyOneMatchAndNeverChangesFileOnFailure() throws Exception {
        Path file = Files.writeString(profile.sourcePath().getParent().resolve("edit.txt"), "old old");
        assertThat(call("edit_file",
                "{\"path\":\"edit.txt\",\"old_text\":\"old\",\"new_text\":\"new\"}").success())
                .isFalse();
        assertThat(Files.readString(file)).isEqualTo("old old");
        Files.writeString(file, "before old after");
        assertThat(call("edit_file",
                "{\"path\":\"edit.txt\",\"old_text\":\"old\",\"new_text\":\"new\"}").success())
                .isTrue();
        assertThat(Files.readString(file)).isEqualTo("before new after");
    }

    @Test
    void grepSkipsBinaryAndGlobReturnsAtMostTwoHundredStableResults() throws Exception {
        Path root = Files.createDirectories(profile.sourcePath().getParent().resolve("search"));
        Files.writeString(root.resolve("z.txt"), "needle-z");
        Files.writeString(root.resolve("a.txt"), "needle-a\nother");
        Files.write(root.resolve("binary.dat"), new byte[] {0, 1, 2, 3});
        assertThat(call("grep", "{\"path\":\"search\",\"pattern\":\"needle-[az]\"}").content())
                .contains("a.txt:1:needle-a", "z.txt:1:needle-z")
                .doesNotContain("binary.dat");

        for (int index = 0; index < 205; index++) {
            Files.writeString(root.resolve("item-%03d.md".formatted(index)), "x");
        }
        String glob = call("glob", "{\"path\":\"search\",\"pattern\":\"*.md\"}").content();
        assertThat(glob).contains("\"truncated\":true", "item-000.md", "item-199.md")
                .doesNotContain("item-200.md");
    }

    @Test
    void malformedUnknownTraversalAndMissingParentInputsFailWithoutMutation() throws Exception {
        Path file = Files.writeString(profile.sourcePath().getParent().resolve("safe.txt"), "safe");
        for (String input : List.of("[]", "{} {}", "{\"path\":\"safe.txt\",\"extra\":1}",
                "{\"path\":\"../safe.txt\"}")) {
            assertThat(call("read_file", input).success()).isFalse();
        }
        assertThat(call("write_file",
                "{\"path\":\"missing/file.txt\",\"content\":\"x\"}").success()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("safe");
        assertThat(Files.exists(profile.sourcePath().getParent().resolve("missing/file.txt"))).isFalse();
    }

    @Test
    void nativeLinkCannotEscapeAgentBoundary() throws Exception {
        Path outside = Files.writeString(workspace.resolve("outside.txt"), "secret");
        Path link = profile.sourcePath().getParent().resolve("escape.txt");
        try {
            Files.createSymbolicLink(link, outside);
        }
        catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "当前平台不允许创建测试用原生链接");
        }

        assertThat(call("read_file", "{\"path\":\"escape.txt\"}").success()).isFalse();
        assertThat(call("write_file",
                "{\"path\":\"escape.txt\",\"content\":\"changed\"}").success()).isFalse();
        assertThat(Files.readString(outside)).isEqualTo("secret");
    }

    private io.oryxos.core.tool.ToolResult call(String name, String input) {
        OryxTool tool = tools.all().stream().filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
        return tool.execute(input);
    }

    private static String jsonPath(Path path) {
        return "{\"path\":\"" + escaped(path) + "\"}";
    }

    private static String escaped(Path path) {
        return path.toString().replace("\\", "\\\\");
    }
}
