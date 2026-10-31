package io.oryxos.web.memory;

import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemorySnapshot;
import java.util.List;

public record MemoryView(String owner, String backend, String capabilities,
        boolean archivalTruncated, List<EntryView> core, List<EntryView> archival) {

    static MemoryView from(MemorySnapshot snapshot) {
        return new MemoryView(snapshot.owner().key(), snapshot.backend(),
                snapshot.capabilities().name(), snapshot.archivalTruncated(),
                snapshot.core().stream().map(EntryView::from).toList(),
                snapshot.archival().stream().map(EntryView::from).toList());
    }

    public record EntryView(String id, String content, String createdAt) {

        static EntryView from(MemoryEntry entry) {
            return new EntryView(entry.id(), entry.content(), entry.createdAt().toString());
        }
    }
}
