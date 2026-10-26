package io.oryxos.storage.repository;

import io.oryxos.storage.entity.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Long> {
}
