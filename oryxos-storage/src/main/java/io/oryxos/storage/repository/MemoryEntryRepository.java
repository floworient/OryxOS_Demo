package io.oryxos.storage.repository;

import io.oryxos.core.memory.MemoryScope;
import io.oryxos.storage.entity.MemoryEntryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntryEntity, Long> {

    List<MemoryEntryEntity> findByOwnerKeyOrderByCreatedAtAscIdAsc(String ownerKey);

    List<MemoryEntryEntity> findByOwnerKeyAndScopeOrderByCreatedAtAscIdAsc(
            String ownerKey, MemoryScope scope);

    List<MemoryEntryEntity> findByOwnerKeyAndScopeOrderByCreatedAtDescIdDesc(
            String ownerKey, MemoryScope scope, Pageable pageable);

    // SQLite lower() 默认只处理 ASCII；非 ASCII 正文保留为候选，由 Java ROOT 规则精确复核。
    @Query(value = "select * from memory_entries where owner_key = :owner and scope = 'ARCHIVAL' "
            + "and (instr(lower(content), :keyword) > 0 or content glob '*[^ -~]*') "
            + "order by created_at desc, id desc", nativeQuery = true)
    List<MemoryEntryEntity> keywordCandidates(@Param("owner") String owner,
            @Param("keyword") String keyword, Pageable pageable);
}
