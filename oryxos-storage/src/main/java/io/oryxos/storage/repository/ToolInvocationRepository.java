package io.oryxos.storage.repository;

import io.oryxos.storage.entity.ToolInvocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolInvocationRepository extends JpaRepository<ToolInvocation, Long> {
}
