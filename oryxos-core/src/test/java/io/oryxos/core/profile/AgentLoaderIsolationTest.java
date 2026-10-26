package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentLoaderIsolationTest {

    @TempDir
    Path workspace;

    @Test
    void rejectsInvalidAgentsWithoutBlockingValidOnes() throws Exception {
        write("valid", validAgent("valid"));
        write("wrong-name", validAgent("different"));
        write("legacy", validAgent("legacy").replace("description: valid", "description: valid\nskills: [old]"));
        write("broken", "---\nname: [\n---\n");

        assertThat(new AgentLoader().loadAll(workspace))
                .extracting(Profile::name)
                .containsExactly("valid");
    }

    @Test
    void realPathBoundaryRejectsResolvedTargetsOutsideAgentsRoot() throws Exception {
        Path agents = Files.createDirectories(workspace.resolve("agents")).toRealPath();
        Path outside = Files.createDirectories(workspace.resolve("outside")).toRealPath();

        assertThat(AgentLoader.isWithinRealBoundary(agents, outside)).isFalse();
    }

    private void write(String directory, String content) throws Exception {
        Path dir = Files.createDirectories(workspace.resolve("agents").resolve(directory));
        Files.writeString(dir.resolve("AGENT.md"), content);
    }

    private static String validAgent(String name) {
        return """
                ---
                name: %s
                description: valid
                identity: {agent_name: Valid, prompt: Help}
                provider: {name: qwen, model: qwen-plus}
                ---
                body
                """.formatted(name);
    }
}
