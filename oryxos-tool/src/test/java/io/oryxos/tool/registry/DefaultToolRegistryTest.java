package io.oryxos.tool.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultToolRegistryTest {

    @Test
    void sortsByNameAndRejectsDuplicateNamesAcrossSources() {
        var registry = new DefaultToolRegistry();
        var read = registration("read_file", ToolSource.BUILTIN, "builtin");
        var java = registration("query_order", ToolSource.JAVA, "orderTools");
        registry.register(read);
        registry.register(java);

        assertThat(registry.registrations()).containsExactly(java, read);
        assertThat(registry.find("read_file")).contains(read);
        assertThat(registry.find("missing")).isEmpty();
        assertThatThrownBy(() -> registry.register(registration("read_file", ToolSource.MCP, "files")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read_file");
    }

    @Test
    void builtinAndJavaUseToolNamesWhileMcpUsesAuthorizedServerNames() {
        var registry = new DefaultToolRegistry();
        var read = registration("read_file", ToolSource.BUILTIN, "builtin");
        var java = registration("query_order", ToolSource.JAVA, "orderTools");
        var mcp = registration("github_report", ToolSource.MCP, "github");
        registry.register(read);
        registry.register(java);
        registry.register(mcp);

        var profile = mock(Profile.class);
        when(profile.tools()).thenReturn(List.of("read_file", "query_order", "github_report"));
        when(profile.mcpServers()).thenReturn(List.of());
        assertThat(registry.visibleTo(profile)).containsExactly(java, read);

        when(profile.tools()).thenReturn(List.of());
        when(profile.mcpServers()).thenReturn(List.of("github"));
        assertThat(registry.visibleTo(profile)).containsExactly(mcp);
    }

    @Test
    void returnedCollectionsAreImmutableSnapshots() {
        var registry = new DefaultToolRegistry();
        registry.register(registration("read_file", ToolSource.BUILTIN, "builtin"));

        assertThatThrownBy(() -> registry.registrations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankMetadataAndNonObjectOrTrailingJsonSchema() {
        var registry = new DefaultToolRegistry();

        assertThatThrownBy(() -> registry.register(registration("blank_description", ToolSource.JAVA,
                "bean", " ", "{\"type\":\"object\"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(registration("array_schema", ToolSource.JAVA,
                "bean", "说明", "[]"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(registration("trailing_schema", ToolSource.JAVA,
                "bean", "说明", "{} {}"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static ToolRegistration registration(String name, ToolSource source, String sourceName) {
        return registration(name, source, sourceName, "说明", "{\"type\":\"object\"}");
    }

    private static ToolRegistration registration(String name, ToolSource source, String sourceName,
            String description, String schema) {
        OryxTool tool = mock(OryxTool.class);
        when(tool.getName()).thenReturn(name);
        when(tool.getDescription()).thenReturn(description);
        when(tool.getInputSchema()).thenReturn(schema);
        return new ToolRegistration(tool, source, sourceName);
    }
}
