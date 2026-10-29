package io.oryxos.storage;

import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.core.notify.NotifyChannelConflictException;
import io.oryxos.core.notify.NotifyChannelNotFoundException;
import io.oryxos.core.notify.NotifyChannelRegistry;
import io.oryxos.storage.entity.NotifyChannelEntity;
import io.oryxos.storage.repository.NotifyChannelJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaNotifyChannelRegistry implements NotifyChannelRegistry {

    private final NotifyChannelJpaRepository repository;

    private final EntityManager entityManager;

    public JpaNotifyChannelRegistry(NotifyChannelJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public NotifyChannel create(NotifyChannel channel) {
        try {
            NotifyChannelEntity entity = new NotifyChannelEntity(channel);
            entityManager.persist(entity);
            entityManager.flush();
            return entity.toDomain();
        }
        catch (PersistenceException exception) {
            throw new NotifyChannelConflictException("通知渠道名称已存在", exception);
        }
    }

    @Override
    @Transactional
    public NotifyChannel update(NotifyChannel channel) {
        if (!repository.existsById(channel.name())) {
            throw new NotifyChannelNotFoundException("通知渠道不存在");
        }
        return repository.saveAndFlush(new NotifyChannelEntity(channel)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotifyChannel> find(String name) {
        return repository.findById(name).map(NotifyChannelEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotifyChannel> list() {
        return repository.findAllByOrderByCreatedAtAscNameAsc().stream()
                .map(NotifyChannelEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String name) {
        repository.deleteById(name);
        repository.flush();
    }
}
