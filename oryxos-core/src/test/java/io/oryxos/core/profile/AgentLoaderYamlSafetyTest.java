package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentLoaderYamlSafetyTest {

    @TempDir
    Path workspace;

    @Test
    void duplicateKeysAndArbitraryTypesAreRejected() throws Exception {
        write("duplicate", "---\nname: duplicate\nname: duplicate\n---\n");
        write("tag", "---\nname: !!java.lang.String tag\n---\n");

        assertThat(new AgentLoader().loadAll(workspace)).isEmpty();
    }

    @Test
    void excessiveAliasesAreRejected() throws Exception {
        String aliases = String.join(", ", java.util.Collections.nCopies(51, "*value"));
        write("aliases", validPrefix("aliases") + "anchor: &value [x]\nitems: [" + aliases + "]\n---\n");

        assertThat(new AgentLoader().loadAll(workspace)).isEmpty();
    }

    @Test
    void excessiveNestingAndCodePointsAreRejected() throws Exception {
        write("nested", validPrefix("nested") + "extra: " + "[".repeat(51) + "x" + "]".repeat(51) + "\n---\n");
        write("large", validPrefix("large") + "extra: " + "x".repeat(3_145_729) + "\n---\n");

        assertThat(new AgentLoader().loadAll(workspace)).isEmpty();
    }

    private void write(String name, String content) throws Exception {
        Path dir = Files.createDirectories(workspace.resolve("agents").resolve(name));
        Files.writeString(dir.resolve("AGENT.md"), content);
    }

    private static String validPrefix(String name) {
        return "---\nname: " + name + "\ndescription: valid\n"
                + "identity: {agent_name: Valid, prompt: Help}\n"
                + "provider: {name: qwen, model: qwen-plus}\n";
    }
}
