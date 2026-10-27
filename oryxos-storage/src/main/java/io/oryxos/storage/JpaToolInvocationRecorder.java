package io.oryxos.storage;

import io.oryxos.core.audit.ToolInvocationRecord;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.storage.entity.ToolInvocation;
import io.oryxos.storage.repository.ToolInvocationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaToolInvocationRecorder implements ToolInvocationRecorder {

    private final ToolInvocationRepository repository;

    public JpaToolInvocationRecorder(ToolInvocationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(ToolInvocationRecord record) {
        repository.saveAndFlush(new ToolInvocation(record));
    }
}
