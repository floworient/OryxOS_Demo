package io.oryxos.storage.entity;

import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "memory_entries")
public class MemoryEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long id;

    @Column(name = "owner_key", nullable = false, length = 64)
    private String ownerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemoryScope scope;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemoryEntryEntity() {
    }

    public MemoryEntryEntity(MemoryOwner owner, MemoryScope scope, String content, Instant createdAt) {
        this.ownerKey = owner.key();
        this.scope = scope;
        this.content = content;
        this.createdAt = createdAt;
    }

    public MemoryEntry toDomain() {
        MemoryOwner owner = "__global__".equals(ownerKey)
                ? MemoryOwner.global() : MemoryOwner.agent(ownerKey);
        return new MemoryEntry("sqlite:" + id, owner, scope, content, createdAt);
    }
}
