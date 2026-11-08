package io.oryxos.storage.repository;

import io.oryxos.storage.entity.SessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    List<SessionEntity> findAllByOrderByLastActiveAtDescIdAsc();

    List<SessionEntity> findAllByOrderByLastActiveAtDescIdAsc(Pageable pageable);

    List<SessionEntity> findByStatusOrderByLastActiveAtDescIdAsc(String status, Pageable pageable);

    Optional<SessionEntity> findByIdAndStatus(String id, String status);

    Optional<SessionEntity> findFirstByChannelAndUserIdAndProfileNameAndStatus(
            String channel, String userId, String profileName, String status);
}
