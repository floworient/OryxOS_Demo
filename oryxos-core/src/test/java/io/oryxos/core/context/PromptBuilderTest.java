package io.oryxos.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

class PromptBuilderTest {

    @Test
    void contextOrderTimeCompleteTurnsAndVisibleToolsArePreserved() {
        var profile = mock(Profile.class);
        when(profile.settings()).thenReturn(new Profile.Settings(10, 20));
        when(profile.tools()).thenReturn(List.of("lookup"));
        var loader = mock(ContextLoader.class);
        when(loader.load(profile)).thenReturn("系统指令");
        var callback = mock(ToolCallback.class);
        var tool = mock(OryxTool.class);
        var clock = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var builder = new PromptBuilder(loader, Map.of("lookup", tool), value -> callback,
                (session, current) -> "记忆哨兵", clock);
        var session = new Session("cli", "u", "weather");
        for (int index = 0; index < 21; index++) {
            session.append("user-" + index);
            session.appendAssistant("answer-" + index);
        }
        var call = new ToolCallIntent("x", "lookup", "{}");
        session.append(new ProviderResponse("p", "m", "", TokenUsage.UNKNOWN, List.of(call), 0));
        session.appendToolResult(call, new ToolResult(true, "ok", null, false));
        var prompt = builder.build(session, profile);
        assertThat(prompt.getInstructions()).hasSize(44);
        assertThat(prompt.getInstructions().getFirst().getText())
                .startsWith("系统指令").endsWith("2026-10-26T08:00+08:00[Asia/Shanghai]");
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo("记忆哨兵");
        assertThat(prompt.getInstructions().get(2).getText()).isEqualTo("user-1");
        assertThat(prompt.getInstructions().getLast()).isInstanceOf(ToolResponseMessage.class);
        assertThat(session.messages()).hasSize(44);
        var options = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getToolCallbacks()).containsExactly(callback);
    }

    @Test
    void emptyMemoryIsOmittedAndUnknownDeclaredToolFails() {
        var profile = mock(Profile.class);
        when(profile.settings()).thenReturn(new Profile.Settings(10, 20));
        when(profile.tools()).thenReturn(List.of());
        var loader = mock(ContextLoader.class);
        when(loader.load(profile)).thenReturn("system");
        var builder = new PromptBuilder(loader, Map.of(), value -> null,
                (session, current) -> "", Clock.systemUTC());
        assertThat(builder.build(new Session("cli", "u", "p"), profile).getInstructions()).hasSize(1);
        when(profile.tools()).thenReturn(List.of("unknown"));
        assertThatThrownBy(() -> builder.build(new Session("cli", "u", "p"), profile))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unknown");
    }
}
