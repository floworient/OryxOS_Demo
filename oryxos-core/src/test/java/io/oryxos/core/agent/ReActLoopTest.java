package io.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.context.PromptBuilder;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;

class ReActLoopTest {

    private final PromptBuilder prompts = mock(PromptBuilder.class);

    private final ProviderService provider = mock(ProviderService.class);

    private final ToolExecutor tools = mock(ToolExecutor.class);

    private final Session session = new Session("cli", "u", "weather");

    private final ReActLoop loop = new ReActLoop(prompts, provider, tools);

    private Profile profile(int max) {
        var profile = mock(Profile.class);
        when(profile.settings()).thenReturn(new Profile.Settings(max, 20));
        when(prompts.build(any(), any())).thenAnswer(call -> new Prompt(session.messages()));
        return profile;
    }

    private ProviderResponse response(String text, ToolCallIntent... calls) {
        return new ProviderResponse("p", "m", text, TokenUsage.UNKNOWN, List.of(calls), 0);
    }

    @Test
    void directAndEmptyAnswersStopAfterOneCall() {
        var profile = profile(10);
        when(provider.chat(any(), any(), any())).thenReturn(response("你好"), response(""));
        assertThat(loop.run(session, "hi", profile)).isEqualTo("你好");
        assertThat(loop.run(session, "empty", profile)).isEmpty();
        assertThat(session.messages()).hasSize(4);
        verify(provider, times(2)).chat(eq(session.id()), eq(profile), any());
    }

    @Test
    void multipleToolsExecuteInOrderAndReachTheNextPrompt() {
        var profile = profile(10);
        var first = new ToolCallIntent("a", "first", "{}");
        var second = new ToolCallIntent("b", "second", "{}");
        when(provider.chat(any(), any(), any())).thenReturn(response("", first, second))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(2);
                    assertThat(prompt.getInstructions()).hasSize(4);
                    var result = (ToolResponseMessage) prompt.getInstructions().get(3);
                    assertThat(result.getResponses().getFirst().id()).isEqualTo("b");
                    return response("完成");
                });
        when(tools.execute(any(), any())).thenReturn(new ToolResult(true, "ok", null, false));
        assertThat(loop.run(session, "work", profile)).isEqualTo("完成");
        var order = inOrder(tools);
        order.verify(tools).execute(session.id(), first);
        order.verify(tools).execute(session.id(), second);
        assertThat(session.messages()).hasSize(5);
    }

    @Test
    void endlessToolRequestsStopAtExactlyTenIterationsAndKeepHistory() {
        var profile = profile(10);
        var call = new ToolCallIntent("a", "lookup", "{}");
        when(provider.chat(any(), any(), any())).thenReturn(response("", call));
        when(tools.execute(any(), any())).thenReturn(new ToolResult(false, "", "不可用", false));
        assertThat(loop.run(session, "work", profile)).contains("达到最大轮数");
        verify(provider, times(10)).chat(eq(session.id()), eq(profile), any());
        verify(tools, times(10)).execute(session.id(), call);
        assertThat(session.messages()).hasSize(22);
        assertThat(session.messages().getLast().getText()).contains("达到最大轮数");
    }

    @Test
    void providerFailureRemainsVisibleAndNoFurtherIterationRuns() {
        var profile = profile(2);
        when(provider.chat(any(), any(), any())).thenThrow(new IllegalStateException("provider timeout"));
        assertThatThrownBy(() -> loop.run(session, "work", profile)).hasMessage("provider timeout");
        assertThat(session.messages()).hasSize(1);
        verify(provider).chat(eq(session.id()), eq(profile), any());
    }
}
