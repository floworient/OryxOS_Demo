package io.oryxos.core.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 所有长期记忆后端必须复用的行为契约。 */
public abstract class LongTermMemoryStoreContract {

    protected abstract LongTermMemoryStore store();

    protected abstract MemoryOwner firstOwner();

    protected abstract MemoryOwner secondOwner();

    @Test
    void isolatesOwnersAndScopes() {
        store().append(firstOwner(), "核心事实", MemoryScope.CORE);
        store().append(firstOwner(), "归档事实", MemoryScope.ARCHIVAL);
        store().append(secondOwner(), "其他 Agent", MemoryScope.ARCHIVAL);

        MemorySnapshot first = store().load(firstOwner());
        MemorySnapshot second = store().load(secondOwner());

        assertThat(first.core()).extracting(MemoryEntry::content).containsExactly("核心事实");
        assertThat(first.archival()).extracting(MemoryEntry::content).containsExactly("归档事实");
        assertThat(second.core()).isEmpty();
        assertThat(second.archival()).extracting(MemoryEntry::content).containsExactly("其他 Agent");
    }

    @Test
    void neverTruncatesOrRecallsCore() {
        String largeCore = "核心".repeat(100);
        store().append(firstOwner(), largeCore, MemoryScope.CORE);
        store().append(firstOwner(), "归档 alpha", MemoryScope.ARCHIVAL);

        assertThat(store().load(firstOwner()).core()).extracting(MemoryEntry::content)
                .containsExactly(largeCore);
        assertThat(store().recallByKeyword(firstOwner(), "核心")).isEmpty();
    }

    @Test
    void keepsNewestCompleteArchivalEntriesWithinContextLimit() {
        store().append(firstOwner(), "first-" + "a".repeat(35), MemoryScope.ARCHIVAL);
        store().append(firstOwner(), "latest-" + "b".repeat(35), MemoryScope.ARCHIVAL);

        MemorySnapshot snapshot = store().load(firstOwner());

        assertThat(snapshot.archival()).extracting(MemoryEntry::content)
                .containsExactly("latest-" + "b".repeat(35));
        assertThat(snapshot.archivalTruncated()).isTrue();
        assertThat(store().archivalEntries(firstOwner())).hasSize(2);
    }

    @Test
    void recallIsCaseInsensitiveAndChoosesNewestMatchesThenReturnsAscending() {
        store().append(firstOwner(), "first Alpha", MemoryScope.ARCHIVAL);
        store().append(firstOwner(), "second alpha", MemoryScope.ARCHIVAL);
        store().append(firstOwner(), "third ALPHA", MemoryScope.ARCHIVAL);

        List<MemoryEntry> recalled = store().recallByKeyword(firstOwner(), "aLpHa");

        assertThat(recalled).extracting(MemoryEntry::content)
                .containsExactly("second alpha", "third ALPHA");
    }

    @Test
    void readsBackendAgainAfterAnAppend() {
        assertThat(store().load(firstOwner()).archival()).isEmpty();

        store().append(firstOwner(), "后来写入", MemoryScope.ARCHIVAL);

        assertThat(store().load(firstOwner()).archival())
                .extracting(MemoryEntry::content).containsExactly("后来写入");
    }
}
