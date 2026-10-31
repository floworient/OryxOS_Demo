package io.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryContext;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.memory.MemorySnapshot;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.session.Session;
import io.oryxos.tool.adapter.AnnotatedToolDiscovery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MemoryToolsTest {

    private final RecordingMemoryService memory = new RecordingMemoryService();

    @AfterEach
    void clearProfile() {
        ProfileContext.clear();
    }

    @Test
    void generatedSchemasUsePublicNamesAndRejectUnknownOrWrongTypes() {
        var tools = new AnnotatedToolDiscovery().discover(List.of(new MemoryTools(memory)));
        var save = tools.stream().filter(tool -> tool.getName().equals("save_memory")).findFirst().orElseThrow();
        var recall = tools.stream().filter(tool -> tool.getName().equals("recall_memory")).findFirst().orElseThrow();
        ProfileContext.set(org.mockito.Mockito.mock(Profile.class));

        assertThat(save.getInputSchema()).contains("content", "scope").doesNotContain("arg0");
        assertThat(recall.getInputSchema()).contains("keyword").doesNotContain("arg0");
        assertThat(save.execute("{\"content\":\"ok\",\"unknown\":true}").success()).isFalse();
        assertThat(save.execute("{\"content\":12}").success()).isFalse();
        assertThat(recall.execute("{\"keyword\":12}").success()).isFalse();
        assertThat(memory.saved).isEmpty();
    }

    @Test
    void saveDefaultsToArchivalSupportsCoreAndNeverEchoesContent() {
        var save = new AnnotatedToolDiscovery().discover(List.of(new MemoryTools(memory))).stream()
                .filter(tool -> tool.getName().equals("save_memory")).findFirst().orElseThrow();
        Profile profile = org.mockito.Mockito.mock(Profile.class);
        ProfileContext.set(profile);

        var defaultResult = save.execute("{\"content\":\"canary-private\"}");
        var coreResult = save.execute("{\"content\":\"core-private\",\"scope\":\"core\"}");

        assertThat(defaultResult.success()).isTrue();
        assertThat(defaultResult.content()).contains("archival").doesNotContain("canary-private");
        assertThat(coreResult.success()).isTrue();
        assertThat(coreResult.content()).contains("core").doesNotContain("core-private");
        assertThat(memory.saved).containsExactly("ARCHIVAL:canary-private", "CORE:core-private");
    }

    @Test
    void rejectsMissingProfileUnknownScopeBlankAndOversizedInputs() {
        var save = new AnnotatedToolDiscovery().discover(List.of(new MemoryTools(memory))).stream()
                .filter(tool -> tool.getName().equals("save_memory")).findFirst().orElseThrow();

        assertThat(save.execute("{\"content\":\"value\"}").success()).isFalse();
        ProfileContext.set(org.mockito.Mockito.mock(Profile.class));
        assertThat(save.execute("{\"content\":\"value\",\"scope\":\"other\"}").success()).isFalse();
        assertThat(save.execute("{\"content\":\"   \"}").success()).isFalse();
        assertThat(save.execute("{\"content\":\"" + "x".repeat(4001) + "\"}").success()).isFalse();
        assertThat(memory.saved).isEmpty();
    }

    @Test
    void recallFormatsTimestampedArchivalResultsAndEmptyResult() {
        var recall = new AnnotatedToolDiscovery().discover(List.of(new MemoryTools(memory))).stream()
                .filter(tool -> tool.getName().equals("recall_memory")).findFirst().orElseThrow();
        ProfileContext.set(org.mockito.Mockito.mock(Profile.class));
        memory.recalled = List.of(new MemoryEntry("1", MemoryOwner.agent("alpha"), MemoryScope.ARCHIVAL,
                "matched", Instant.parse("2026-10-31T00:00:00Z")));

        assertThat(recall.execute("{\"keyword\":\"match\"}").content())
                .contains("2026-10-31T00:00:00Z", "matched");
        memory.recalled = List.of();
        assertThat(recall.execute("{\"keyword\":\"none\"}").content()).contains("未找到");
        assertThat(recall.execute("{\"keyword\":\"" + "x".repeat(257) + "\"}").success()).isFalse();
    }

    private static final class RecordingMemoryService implements MemoryService {

        private final List<String> saved = new ArrayList<>();

        private List<MemoryEntry> recalled = List.of();

        @Override
        public MemoryContext load(Session session, Profile profile) {
            return new MemoryContext(loadLongTerm(profile), session.messages());
        }

        @Override
        public void save(Profile profile, String content, MemoryScope scope) {
            saved.add(scope + ":" + content);
        }

        @Override
        public List<MemoryEntry> recall(Profile profile, String keyword) {
            return recalled;
        }

        @Override
        public MemorySnapshot loadLongTerm(Profile profile) {
            return new MemorySnapshot(MemoryOwner.global(), List.of(), List.of(), false,
                    "test", MemoryCapabilities.KEYWORD);
        }
    }
}
