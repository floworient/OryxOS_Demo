package io.oryxos.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.oryxos.core.audit.LlmCallPurpose;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionCompaction;
import io.oryxos.core.tool.ToolResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class SessionSummarizerTest {

    @Test
    void rejectsSummaryBudgetThatCannotFitPreviousSummaryAndRequestFraming() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new ContextBudget(8192, 2048, 512, 100, 500, 1024, 2048, 4, 256, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void largeToolOutputDoesNotForceOneSummaryPerEvictedTurn() {
        var provider = mock(ProviderService.class);
        var profile = mock(Profile.class);
        var session = new Session("cli", "u", "p");
        session.append("查询");
        var tool = new ToolCallIntent("id", "lookup", "{}");
        session.append(new ProviderResponse("p", "m", "", TokenUsage.UNKNOWN, List.of(tool), 0));
        session.appendToolResult(tool, new ToolResult(true, "x".repeat(6000), null, false));
        session.appendAssistant("结束");
        session.append("新问题");
        assertThat(new SessionSummarizer(provider, ContextBudget.DEFAULT).compact(session, profile, 4)).isFalse();
        verifyNoInteractions(provider);
    }

    @Test
    void keepsCommittedCursorOnProviderFailureAndPropagatesAuditFailure() {
        var provider = mock(ProviderService.class);
        var profile = mock(Profile.class);
        var route = new Profile.ProviderRef("p", "m", null);
        when(profile.provider()).thenReturn(route);
        var session = new Session("cli", "u", "p");
        for (int i = 0; i < 5; i++) {
            session.append("问题 " + i);
            session.appendAssistant("答复 " + i);
        }
        session.append("当前问题");
        var previous = new SessionCompaction("先前摘要", 2);
        session.updateCompaction(previous);
        var auditFailure = new ProviderException(ProviderErrorCode.AUDIT_PERSISTENCE_FAILED, "p", "m", "audit", null);
        when(provider.chat(eq(session.id()), eq(profile), any(Prompt.class), eq(route), eq(LlmCallPurpose.SUMMARY)))
                .thenThrow(new ProviderException(ProviderErrorCode.TIMEOUT, "p", "m", "timeout", null), auditFailure);
        var summarizer = new SessionSummarizer(provider, ContextBudget.DEFAULT);
        assertThat(summarizer.compact(session, profile, 10)).isFalse();
        assertThat(session.compaction()).contains(previous);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> summarizer.compact(session, profile, 10))
                .isSameAs(auditFailure);
        assertThat(session.compaction()).contains(previous);
    }

    @Test
    void rejectsAnOutputStoppedByTheModelLengthLimit() {
        var provider = mock(ProviderService.class);
        var profile = mock(Profile.class);
        var route = new Profile.ProviderRef("p", "m", null);
        when(profile.provider()).thenReturn(route);
        var session = new Session("cli", "u", "p");
        for (int i = 0; i < 4; i++) {
            session.append("问题 " + i);
            session.appendAssistant("答复 " + i);
        }
        session.append("当前问题");
        when(provider.chat(eq(session.id()), eq(profile), any(Prompt.class), eq(route), eq(LlmCallPurpose.SUMMARY)))
                .thenReturn(new ProviderResponse("p", "m", "未完成摘要", TokenUsage.UNKNOWN, List.of(), 0, "length"));
        assertThat(new SessionSummarizer(provider, ContextBudget.DEFAULT).compact(session, profile, 8)).isFalse();
        assertThat(session.compaction()).isEmpty();
    }

    @Test
    void waitsForBatchAndOnlySendsNewlyEvictedTurns() {
        var provider = mock(ProviderService.class);
        var profile = mock(Profile.class);
        var route = new Profile.ProviderRef("p", "m", null);
        when(profile.provider()).thenReturn(route);
        var session = new Session("cli", "u", "p");
        for (int i = 0; i < 8; i++) {
            session.append("question-" + i);
            session.appendAssistant("answer-" + i);
        }
        session.append("active");
        var summarizer = new SessionSummarizer(provider, ContextBudget.DEFAULT);
        assertThat(summarizer.compact(session, profile, 6)).isFalse();
        verifyNoInteractions(provider);
        var attempts = new AtomicInteger();
        when(provider.chat(eq(session.id()), eq(profile), any(Prompt.class), eq(route), eq(LlmCallPurpose.SUMMARY)))
                .thenAnswer(call -> {
                    Prompt prompt = call.getArgument(2);
                    assertThat(PromptInputEstimator.estimate(prompt)).isLessThanOrEqualTo(4096);
                    String data = prompt.getInstructions().getLast().getText();
                    if (attempts.incrementAndGet() == 1) {
                        assertThat(data).contains("question-0", "question-3").doesNotContain("question-4");
                    } else {
                        assertThat(data).contains("已有摘要", "question-4", "question-7").doesNotContain("question-0");
                    }
                    return new ProviderResponse("p", "m", "已有摘要", TokenUsage.UNKNOWN, List.of(), 0);
                });
        assertThat(summarizer.compact(session, profile, 8)).isTrue();
        assertThat(summarizer.compact(session, profile, 8)).isFalse();
        assertThat(summarizer.compact(session, profile, 16)).isTrue();
        assertThat(session.compaction().orElseThrow().processedUntil()).isEqualTo(16);
        assertThat(session.messages()).hasSize(17);
        assertThat(attempts).hasValue(2);
    }
}
