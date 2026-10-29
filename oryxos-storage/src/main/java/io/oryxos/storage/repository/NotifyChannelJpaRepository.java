package io.oryxos.storage.repository;

import io.oryxos.storage.entity.NotifyChannelEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotifyChannelJpaRepository extends JpaRepository<NotifyChannelEntity, String> {

    List<NotifyChannelEntity> findAllByOrderByCreatedAtAscNameAsc();
}
