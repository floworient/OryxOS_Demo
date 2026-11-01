package io.oryxos.storage.repository;

import io.oryxos.core.memory.MemoryScope;
import io.oryxos.storage.entity.MemoryEntryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntryEntity, Long> {

    List<MemoryEntryEntity> findByOwnerKeyOrderByCreatedAtAscIdAsc(String ownerKey);

    List<MemoryEntryEntity> findByOwnerKeyAndScopeOrderByCreatedAtAscIdAsc(
            String ownerKey, MemoryScope scope);
}
