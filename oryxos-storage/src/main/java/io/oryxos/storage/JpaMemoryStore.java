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
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
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
        try {
            List<MemoryEntry> core = repository
                    .findByOwnerKeyAndScopeOrderByCreatedAtAscIdAsc(owner.key(), MemoryScope.CORE)
                    .stream().map(MemoryEntryEntity::toDomain).toList();
            List<MemoryEntry> selected = new ArrayList<>();
            int used = 0;
            for (int page = 0; ; page++) {
                var batch = repository.findByOwnerKeyAndScopeOrderByCreatedAtDescIdDesc(
                        owner.key(), MemoryScope.ARCHIVAL, PageRequest.of(page, 64));
                for (var entity : batch) {
                    MemoryEntry entry = entity.toDomain();
                    int length = MemoryEntryPolicy.promptLineCharacters(entry.content());
                    if ((long) used + length > archivalMaxCharacters) {
                        return snapshot(owner, core, selected, true);
                    }
                    selected.add(entry);
                    used += length;
                }
                if (batch.size() < 64) {
                    return snapshot(owner, core, selected, false);
                }
            }
        }
        catch (RuntimeException exception) {
            throw mapFailure(exception);
        }
    }

    @Override
    public List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "召回关键词不能为空");
        }
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        try {
            List<MemoryEntry> selected = new ArrayList<>();
            for (int page = 0; ; page++) {
                var batch = repository.keywordCandidates(owner.key(), query, PageRequest.of(page, 64));
                for (var entity : batch) {
                    MemoryEntry entry = entity.toDomain();
                    if (entry.content().toLowerCase(Locale.ROOT).contains(query)) {
                        selected.add(entry);
                        if (selected.size() == recallMaxResults) {
                            return MemoryEntryPolicy.stable(selected);
                        }
                    }
                }
                if (batch.size() < 64) {
                    return MemoryEntryPolicy.stable(selected);
                }
            }
        }
        catch (RuntimeException exception) {
            throw mapFailure(exception);
        }
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

    private MemorySnapshot snapshot(MemoryOwner owner, List<MemoryEntry> core,
            List<MemoryEntry> archival, boolean truncated) {
        return new MemorySnapshot(owner, core, MemoryEntryPolicy.stable(archival), truncated,
                "sqlite", capabilities());
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
