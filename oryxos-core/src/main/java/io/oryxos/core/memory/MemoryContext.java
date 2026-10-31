package io.oryxos.core.memory;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.Message;

public record MemoryContext(MemorySnapshot longTerm, List<Message> sessionMessages) {

    public MemoryContext {
        longTerm = Objects.requireNonNull(longTerm, "长期记忆不能为空");
        sessionMessages = List.copyOf(Objects.requireNonNull(sessionMessages, "会话记忆不能为空"));
    }
}
