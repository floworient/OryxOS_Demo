package io.oryxos.storage;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemorySnapshot;
import io.oryxos.storage.entity.MemoryEntryEntity;
import io.oryxos.storage.repository.MemoryEntryRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** SQLite/JPA 长期记忆后端。 */
public final class JpaMemoryStore implements LongTermMemoryStore {

    private final MemoryEntryRepository repository;

    private final Clock clock;

    private final int archivalMaxCharacters;

    private final int recallMaxResults;

    private final int entryMaxCharacters;

    public JpaMemoryStore(MemoryEntryRepository repository, Clock clock,
            int archivalMaxCharacters, int recallMaxResults, int entryMaxCharacters) {
        this.repository = Objects.requireNonNull(repository, "记忆 Repository 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        requirePositive(archivalMaxCharacters);
        requirePositive(recallMaxResults);
        requirePositive(entryMaxCharacters);
        this.archivalMaxCharacters = archivalMaxCharacters;
        this.recallMaxResults = recallMaxResults;
        this.entryMaxCharacters = entryMaxCharacters;
    }

    @Override
    public void append(MemoryOwner owner, String content, MemoryScope scope) {
        Objects.requireNonNull(owner, "记忆 owner 不能为空");
        Objects.requireNonNull(scope, "记忆 scope 不能为空");
        if (content == null || content.isBlank() || content.trim().length() > entryMaxCharacters) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "记忆内容为空或超过长度上限");
        }
        repository.saveAndFlush(new MemoryEntryEntity(owner, scope, content.trim(), clock.instant()));
    }

    @Override
    public MemorySnapshot load(MemoryOwner owner) {
        List<MemoryEntry> all = entries(owner);
        List<MemoryEntry> core = all.stream().filter(item -> item.scope() == MemoryScope.CORE).toList();
        List<MemoryEntry> archival = all.stream()
                .filter(item -> item.scope() == MemoryScope.ARCHIVAL).toList();
        List<MemoryEntry> selected = newestComplete(archival);
        return new MemorySnapshot(owner, core, selected, selected.size() < archival.size(),
                "sqlite", capabilities());
    }

    @Override
    public List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "召回关键词不能为空");
        }
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        List<MemoryEntry> matches = archivalEntries(owner).stream()
                .filter(item -> item.content().toLowerCase(Locale.ROOT).contains(query)).toList();
        return List.copyOf(matches.subList(Math.max(0, matches.size() - recallMaxResults), matches.size()));
    }

    @Override
    public MemoryCapabilities capabilities() {
        return MemoryCapabilities.KEYWORD;
    }

    @Override
    public List<MemoryEntry> archivalEntries(MemoryOwner owner) {
        return repository.findByOwnerKeyAndScopeOrderByCreatedAtAscIdAsc(owner.key(), MemoryScope.ARCHIVAL)
                .stream().map(MemoryEntryEntity::toDomain).toList();
    }

    private List<MemoryEntry> entries(MemoryOwner owner) {
        return repository.findByOwnerKeyOrderByCreatedAtAscIdAsc(owner.key()).stream()
                .map(MemoryEntryEntity::toDomain).toList();
    }

    private List<MemoryEntry> newestComplete(List<MemoryEntry> entries) {
        List<MemoryEntry> reversed = new ArrayList<>();
        int used = 0;
        for (int index = entries.size() - 1; index >= 0; index--) {
            MemoryEntry entry = entries.get(index);
            int size = ("- [" + entry.createdAt() + "] " + entry.content() + "\n").length();
            if (used + size > archivalMaxCharacters) {
                break;
            }
            reversed.add(0, entry);
            used += size;
        }
        return List.copyOf(reversed);
    }

    private static void requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("记忆上限必须大于 0");
        }
    }
}
