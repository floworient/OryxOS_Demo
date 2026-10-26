package io.oryxos.storage;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.storage.entity.LlmCallEntity;
import io.oryxos.storage.repository.LlmCallRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaLlmCallRecorder implements LlmCallRecorder {

    private final LlmCallRepository repository;

    public JpaLlmCallRecorder(LlmCallRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public LlmCallRecord record(LlmCallRecord record) {
        return repository.saveAndFlush(new LlmCallEntity(record)).toRecord();
    }
}
