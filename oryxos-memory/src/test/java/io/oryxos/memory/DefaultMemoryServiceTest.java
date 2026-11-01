package io.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemorySnapshot;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultMemoryServiceTest {

    @Test
    void loadsLongTermAndImmutableStructuredSessionSnapshotForProfileOwner() {
        var store = new RecordingStore(MemoryCapabilities.KEYWORD);
        var service = new DefaultMemoryService(store, new MemoryOwnerResolver());
        var session = new Session("cli", "user", "alpha");
        session.append("first");

        var context = service.load(session, profile("alpha"));
        session.appendAssistant("later");

        assertThat(store.lastOwner).isEqualTo(MemoryOwner.agent("alpha"));
        assertThat(context.longTerm().owner()).isEqualTo(MemoryOwner.agent("alpha"));
        assertThat(context.sessionMessages()).hasSize(1);
        assertThat(context.sessionMessages().getFirst().getText()).isEqualTo("first");
        assertThatThrownBy(() -> context.sessionMessages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void saveRecallAndLoadLongTermAlwaysResolveOwnerWithoutCaching() {
        var store = new RecordingStore(MemoryCapabilities.KEYWORD);
        var service = new DefaultMemoryService(store, new MemoryOwnerResolver());
        Profile profile = profile("alpha");

        service.save(profile, "preference", MemoryScope.CORE);
        service.recall(profile, "pref");
        service.loadLongTerm(profile);

        assertThat(store.operations).containsExactly("append:alpha:CORE", "recall:alpha", "load:alpha");
        assertThat(store.lastContent).isEqualTo("preference");
    }

    @Test
    void nullProfileUsesIsolatedGlobalOwnerForExplicitInternalCalls() {
        var store = new RecordingStore(MemoryCapabilities.KEYWORD);
        var service = new DefaultMemoryService(store, new MemoryOwnerResolver());

        service.save(null, "global", MemoryScope.ARCHIVAL);

        assertThat(store.lastOwner).isEqualTo(MemoryOwner.global());
    }

    @Test
    void delegatedBackendUsesItsOwnRecallImplementation() {
        var store = new RecordingStore(MemoryCapabilities.DELEGATED);
        var service = new DefaultMemoryService(store, new MemoryOwnerResolver());

        assertThat(service.recall(profile("alpha"), "semantic"))
                .extracting(MemoryEntry::content).containsExactly("recalled:semantic");
        assertThat(store.operations).containsExactly("recall:alpha");
    }

    @Test
    void hybridBuiltinFailsFastUntilFeature015IsInstalled() {
        var store = new RecordingStore(MemoryCapabilities.HYBRID_BUILTIN);

        assertThatThrownBy(() -> new DefaultMemoryService(store, new MemoryOwnerResolver()))
                .isInstanceOf(MemoryException.class)
                .extracting(exception -> ((MemoryException) exception).code())
                .isEqualTo(MemoryException.Code.UNSUPPORTED_CAPABILITY);
    }

    @Test
    void backendFailureIsNotConvertedToEmptyMemory() {
        LongTermMemoryStore store = new RecordingStore(MemoryCapabilities.KEYWORD) {
            @Override
            public MemorySnapshot load(MemoryOwner owner) {
                throw new MemoryException(MemoryException.Code.STORE_UNAVAILABLE, "backend unavailable");
            }
        };
        var service = new DefaultMemoryService(store, new MemoryOwnerResolver());

        assertThatThrownBy(() -> service.loadLongTerm(profile("alpha")))
                .isInstanceOf(MemoryException.class)
                .hasMessage("backend unavailable");
    }

    private static Profile profile(String name) {
        return new Profile(name, "test", new Profile.Identity(name, "prompt"),
                new Profile.ProviderRef("test", "model", null), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(10, 20), Path.of("agents", name, "AGENT.md"));
    }

    private static class RecordingStore implements LongTermMemoryStore {

        private final MemoryCapabilities capabilities;

        private final List<String> operations = new ArrayList<>();

        private MemoryOwner lastOwner;

        private String lastContent;

        RecordingStore(MemoryCapabilities capabilities) {
            this.capabilities = capabilities;
        }

        @Override
        public void append(MemoryOwner owner, String content, MemoryScope scope) {
            lastOwner = owner;
            lastContent = content;
            operations.add("append:" + owner.key() + ":" + scope);
        }

        @Override
        public MemorySnapshot load(MemoryOwner owner) {
            lastOwner = owner;
            operations.add("load:" + owner.key());
            return new MemorySnapshot(owner, List.of(), List.of(), false, "recording", capabilities);
        }

        @Override
        public List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword) {
            lastOwner = owner;
            operations.add("recall:" + owner.key());
            return List.of(new MemoryEntry("1", owner, MemoryScope.ARCHIVAL,
                    "recalled:" + keyword, Instant.EPOCH));
        }

        @Override
        public MemoryCapabilities capabilities() {
            return capabilities;
        }

        @Override
        public List<MemoryEntry> archivalEntries(MemoryOwner owner) {
            return List.of();
        }
    }
}
