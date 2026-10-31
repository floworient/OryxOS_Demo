package io.oryxos.core.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

class MemoryContractsTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-31T01:30:00Z");

    @Test
    void createsAgentAndGlobalOwnersWithoutAliasingThem() {
        MemoryOwner agent = MemoryOwner.agent("daily-tech");
        MemoryOwner global = MemoryOwner.global();

        assertThat(agent.key()).isEqualTo("daily-tech");
        assertThat(agent.agentScoped()).isTrue();
        assertThat(global.key()).isEqualTo("__global__");
        assertThat(global.agentScoped()).isFalse();
        assertThat(agent).isNotEqualTo(global);
    }

    @Test
    void rejectsReservedGlobalKeyAsAgentName() {
        assertThatThrownBy(() -> MemoryOwner.agent("__global__"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("保留");
    }

    @Test
    void entryRequiresConsistentNonBlankValues() {
        MemoryOwner owner = MemoryOwner.agent("daily-tech");

        assertThatThrownBy(() -> new MemoryEntry(" ", owner, MemoryScope.CORE, "fact", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryEntry("1", owner, MemoryScope.CORE, " ", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryEntry("1", null, MemoryScope.CORE, "fact", CREATED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void snapshotAndContextDefensivelyCopyCollections() {
        MemoryOwner owner = MemoryOwner.agent("daily-tech");
        MemoryEntry entry = new MemoryEntry("1", owner, MemoryScope.CORE, "核心事实", CREATED_AT);
        List<MemoryEntry> core = new ArrayList<>(List.of(entry));
        List<Message> messages = new ArrayList<>(List.of(new UserMessage("hello")));

        MemorySnapshot snapshot = new MemorySnapshot(owner, core, List.of(), false,
                "markdown", MemoryCapabilities.KEYWORD);
        MemoryContext context = new MemoryContext(snapshot, messages);
        core.clear();
        messages.clear();

        assertThat(snapshot.core()).containsExactly(entry);
        assertThat(context.sessionMessages()).hasSize(1);
        assertThatThrownBy(() -> snapshot.core().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.sessionMessages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void memoryExceptionCarriesStableCodeAndSafeMessage() {
        MemoryException failure = new MemoryException(
                MemoryException.Code.STORE_UNAVAILABLE, "长期记忆暂不可用");

        assertThat(failure.code()).isEqualTo(MemoryException.Code.STORE_UNAVAILABLE);
        assertThat(failure).hasMessage("长期记忆暂不可用");
    }
}
