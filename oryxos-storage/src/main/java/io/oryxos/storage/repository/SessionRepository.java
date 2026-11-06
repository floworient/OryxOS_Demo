package io.oryxos.storage.repository;

import io.oryxos.storage.entity.SessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    List<SessionEntity> findAllByOrderByLastActiveAtDescIdAsc();

    Optional<SessionEntity> findByIdAndStatus(String id, String status);

    Optional<SessionEntity> findFirstByChannelAndUserIdAndProfileNameAndStatus(
            String channel, String userId, String profileName, String status);
}
