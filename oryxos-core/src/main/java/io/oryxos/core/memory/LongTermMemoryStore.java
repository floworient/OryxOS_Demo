package io.oryxos.core.memory;

import java.util.List;

public interface LongTermMemoryStore {

    void append(MemoryOwner owner, String content, MemoryScope scope);

    MemorySnapshot load(MemoryOwner owner);

    List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword);

    MemoryCapabilities capabilities();

    List<MemoryEntry> archivalEntries(MemoryOwner owner);
}
