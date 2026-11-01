package io.oryxos.storage;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryEntryPolicy;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemorySnapshot;
import io.oryxos.storage.entity.MemoryEntryEntity;
import io.oryxos.storage.repository.MemoryEntryRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;

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
        String normalized = MemoryEntryPolicy.normalizeContent(content, entryMaxCharacters);
        try {
            repository.saveAndFlush(new MemoryEntryEntity(owner, scope, normalized, clock.instant()));
        }
        catch (RuntimeException exception) {
            throw mapFailure(exception);
        }
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
        return MemoryEntryPolicy.latestKeywordMatches(archivalEntries(owner), keyword, recallMaxResults);
    }

    @Override
    public MemoryCapabilities capabilities() {
        return MemoryCapabilities.KEYWORD;
    }

    @Override
    public List<MemoryEntry> archivalEntries(MemoryOwner owner) {
        try {
            return MemoryEntryPolicy.stable(repository
                    .findByOwnerKeyAndScopeOrderByCreatedAtAscIdAsc(owner.key(), MemoryScope.ARCHIVAL)
                    .stream().map(MemoryEntryEntity::toDomain).toList());
        }
        catch (RuntimeException exception) {
            throw mapFailure(exception);
        }
    }

    private List<MemoryEntry> entries(MemoryOwner owner) {
        try {
            return MemoryEntryPolicy.stable(repository.findByOwnerKeyOrderByCreatedAtAscIdAsc(owner.key())
                    .stream().map(MemoryEntryEntity::toDomain).toList());
        }
        catch (RuntimeException exception) {
            throw mapFailure(exception);
        }
    }

    private List<MemoryEntry> newestComplete(List<MemoryEntry> entries) {
        return MemoryEntryPolicy.newestWithinPromptLimit(entries, archivalMaxCharacters);
    }

    private static MemoryException mapFailure(RuntimeException exception) {
        if (exception instanceof MemoryException memory) {
            return memory;
        }
        if (exception instanceof DataAccessException) {
            return new MemoryException(MemoryException.Code.STORE_UNAVAILABLE,
                    "SQLite 记忆存储不可用", exception);
        }
        return new MemoryException(MemoryException.Code.MALFORMED_STORE,
                "SQLite 记忆数据无效", exception);
    }

    private static void requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("记忆上限必须大于 0");
        }
    }
}
