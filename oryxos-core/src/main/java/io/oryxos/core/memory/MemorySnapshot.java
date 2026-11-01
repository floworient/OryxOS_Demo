package io.oryxos.core.memory;

import java.util.List;
import java.util.Objects;

public record MemorySnapshot(MemoryOwner owner, List<MemoryEntry> core,
        List<MemoryEntry> archival, boolean archivalTruncated,
        String backend, MemoryCapabilities capabilities) {

    public MemorySnapshot {
        owner = Objects.requireNonNull(owner, "记忆 owner 不能为空");
        core = List.copyOf(Objects.requireNonNull(core, "核心记忆不能为空"));
        archival = List.copyOf(Objects.requireNonNull(archival, "归档记忆不能为空"));
        if (backend == null || backend.isBlank()) {
            throw new IllegalArgumentException("记忆后端不能为空");
        }
        backend = backend.trim();
        capabilities = Objects.requireNonNull(capabilities, "记忆能力不能为空");
        requireEntries(owner, core, MemoryScope.CORE);
        requireEntries(owner, archival, MemoryScope.ARCHIVAL);
    }

    private static void requireEntries(MemoryOwner owner, List<MemoryEntry> entries, MemoryScope scope) {
        if (entries.stream().anyMatch(entry -> !owner.equals(entry.owner()) || scope != entry.scope())) {
            throw new IllegalArgumentException("记忆快照包含其他 owner 或分区的条目");
        }
    }
}
