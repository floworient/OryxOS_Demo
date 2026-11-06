package io.oryxos.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.tool.ToolResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

class SessionTest {

    @Test
    void identityAndToolAssociationSurviveMessageAccumulation() {
        var session = new Session("cli", "alice", "weather");
        var call = new ToolCallIntent("call-7", "lookup", "{}");
        session.append("天气");
        session.append(new ProviderResponse("p", "m", "", TokenUsage.UNKNOWN, List.of(call), 0));
        session.appendToolResult(call, new ToolResult(true, "晴", null, false));
        assertThat(session.messages()).hasSize(3);
        var assistant = (AssistantMessage) session.messages().get(1);
        assertThat(assistant.getToolCalls().getFirst().id()).isEqualTo("call-7");
        var tool = (ToolResponseMessage) session.messages().get(2);
        assertThat(tool.getResponses().getFirst().id()).isEqualTo("call-7");
        assertThat(tool.getResponses().getFirst().name()).isEqualTo("lookup");
        assertThat(tool.getResponses().getFirst().responseData()).contains("晴");
        assertThat(UUID.fromString(session.id()).toString()).isEqualTo(session.id());
        assertThat(new Session("cli", "alice", "weather").id()).isNotEqualTo(session.id());
        assertThat(new Session("web", "alice", "weather").id()).isNotEqualTo(session.id());
        assertThat(new Session("cli", "bob", "weather").id()).isNotEqualTo(session.id());
        assertThat(new Session("cli", "alice", "other").id()).isNotEqualTo(session.id());
        assertThat(new Session("a:b", "c", "d").id()).isNotEqualTo(new Session("a", "b:c", "d").id());
        assertThatThrownBy(() -> session.messages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restoredMessageCollectionsCannotMutateStoredHistory() {
        var calls = new java.util.ArrayList<AssistantMessage.ToolCall>();
        calls.add(new AssistantMessage.ToolCall("c", "function", "lookup", "{}"));
        var message = AssistantMessage.builder().content("").toolCalls(calls).build();
        var session = new Session("cli", "u", "p", java.time.Instant.now(), java.time.Instant.now(),
                List.of(message));
        calls.clear();
        assertThat(((AssistantMessage) session.messages().getFirst()).getToolCalls()).hasSize(1);
        assertThatThrownBy(() -> ((AssistantMessage) session.messages().getFirst()).getToolCalls().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        session.messages().getFirst().getMetadata().put("injected", "x");
        assertThat(session.messages().getFirst().getMetadata()).doesNotContainKey("injected");
    }

    @Test
    void assistantToolCallWithoutTextRetainsEmptyContentInSnapshot() {
        var message = AssistantMessage.builder().toolCalls(List.of(
                new AssistantMessage.ToolCall("c", "function", "lookup", "{}"))).build();
        var session = new Session("cli", "u", "p", java.time.Instant.now(), java.time.Instant.now(),
                List.of(message));
        assertThat(session.messages().getFirst().getText()).isEmpty();
    }

    @Test
    void emptyIdentityIsRejected() {
        assertThatThrownBy(() -> new Session("", "u", "p")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitIdentityRestoresPersistedGenerationAndRejectsInvalidState() {
        String id = "33333333-3333-4333-8333-333333333333";
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Instant lastActiveAt = Instant.parse("2026-11-04T00:01:00Z");
        var restored = new Session(id, "web", "alice", "weather", createdAt, lastActiveAt,
                List.of(new UserMessage("历史消息")));

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.messages()).singleElement()
                .satisfies(message -> assertThat(message.getText()).isEqualTo("历史消息"));
        assertThatThrownBy(() -> new Session("not-a-uuid", "web", "alice", "weather",
                createdAt, lastActiveAt, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Session(id, "web", "alice", "weather", lastActiveAt,
                createdAt, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionDetailsEnforcesStatusTimesAndDefensiveMessages() {
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Instant lastActiveAt = Instant.parse("2026-11-04T00:01:00Z");
        Instant archivedAt = Instant.parse("2026-11-04T00:02:00Z");
        var source = new java.util.ArrayList<org.springframework.ai.chat.messages.Message>();
        source.add(new UserMessage("历史"));

        var archived = new SessionDetails("33333333-3333-4333-8333-333333333333",
                "web", "alice", "weather", SessionStatus.ARCHIVED,
                createdAt, lastActiveAt, archivedAt, source);
        source.clear();

        assertThat(archived.messages()).singleElement()
                .satisfies(message -> assertThat(message.getText()).isEqualTo("历史"));
        assertThatThrownBy(() -> archived.messages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new SessionDetails(archived.id(), "web", "alice", "weather",
                SessionStatus.ACTIVE, createdAt, lastActiveAt, archivedAt, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionDetails(archived.id(), "web", "alice", "weather",
                SessionStatus.ARCHIVED, createdAt, lastActiveAt, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionDetails(archived.id(), "web", "alice", "weather",
                SessionStatus.ARCHIVED, createdAt, lastActiveAt, createdAt.minusSeconds(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
