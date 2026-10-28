package io.oryxos.storage.repository;

import io.oryxos.storage.entity.SessionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    List<SessionEntity> findAllByOrderByLastActiveAtDescIdAsc();
}
