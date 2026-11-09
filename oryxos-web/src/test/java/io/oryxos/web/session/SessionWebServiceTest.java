package io.oryxos.web.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionAcquisition;
import io.oryxos.core.session.SessionDetails;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.session.SessionStatus;
import io.oryxos.core.session.SessionSummary;
import io.oryxos.web.api.AgentCallExecutor;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.Utf8MessageValidator;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

class SessionWebServiceTest {

    private final ProfileRegistry profiles = new ProfileRegistry();

    private final SessionManager sessions = mock(SessionManager.class);

    private final AgentService agents = mock(AgentService.class);

    private final java.util.concurrent.ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    private SessionWebService service;

    @BeforeEach
    void setUp() {
        profiles.register(profile("daily-tech"));
        service = new SessionWebService(profiles, sessions, agents,
                new AgentCallExecutor(executor, Duration.ofSeconds(2)),
                new Utf8MessageValidator());
    }

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void createsOrReusesActiveSessionAndRejectsUnknownAgent() {
        var session = new Session("web", "user-1", "daily-tech");
        when(sessions.acquire("web", "user-1", "daily-tech"))
                .thenReturn(new SessionAcquisition(session.id(), true),
                        new SessionAcquisition(session.id(), false));
        when(sessions.find(session.id())).thenReturn(Optional.of(session));
        when(sessions.details(session.id())).thenReturn(Optional.of(details(session)));

        var created = service.create("daily-tech", "user-1");
        var reused = service.create("daily-tech", "user-1");

        assertThat(created.created()).isTrue();
        assertThat(reused.created()).isFalse();
        assertThat(created.session().id()).isEqualTo(session.id());
        assertThatThrownBy(() -> service.create("missing", "user-1"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("AGENT_NOT_FOUND"));
    }

    @Test
    void exposesOnlyTheLatestHundredSafeMessageProjections() {
        var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
        for (int index = 0; index < 99; index++) {
            messages.add(new UserMessage("u" + index));
        }
        messages.add(AssistantMessage.builder().content("调用工具")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "lookup", "{\"secret\":true}"))).build());
        messages.add(ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse(
                        "call-1", "lookup", "{\"responseData\":\"secret\"}"))).build());
        var details = new SessionDetails(java.util.UUID.randomUUID().toString(), "cli", "user-1",
                "daily-tech", SessionStatus.ARCHIVED, Instant.parse("2026-11-04T00:00:00Z"),
                Instant.parse("2026-11-04T00:01:00Z"),
                Instant.parse("2026-11-04T00:02:00Z"), messages);
        when(sessions.details(details.id())).thenReturn(Optional.of(details));

        var view = service.get(details.id());

        assertThat(view.historyTruncated()).isTrue();
        assertThat(view.messages()).hasSize(100);
        assertThat(view.messages().getFirst().text()).isEqualTo("u1");
        assertThat(view.messages().get(98).toolCalls())
                .containsExactly(new MessageView.ToolReferenceView("lookup", "call-1"));
        assertThat(view.messages().get(99).toolResults())
                .containsExactly(new MessageView.ToolReferenceView("lookup", "call-1"));
    }

    @Test
    void sendsOnlyToActiveWebSessionAndUsesTrimmedMessage() {
        var web = new Session("web", "user-1", "daily-tech");
        var cli = new Session("cli", "user-1", "daily-tech");
        when(sessions.find(web.id())).thenReturn(Optional.of(web));
        when(sessions.find(cli.id())).thenReturn(Optional.of(cli));
        when(agents.process(web, "hello")).thenReturn("reply");

        assertThat(service.send(web.id(), "  hello  ").reply()).isEqualTo("reply");
        verify(agents).process(web, "hello");
        assertThatThrownBy(() -> service.send(cli.id(), "hello"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("SESSION_CHANNEL_NOT_WRITABLE"));
        verify(agents, never()).process(cli, "hello");
    }

    @Test
    void listsRecentSessionSummariesWithOnlyThePublicActiveFilter() {
        var summary = new SessionSummary(java.util.UUID.randomUUID().toString(), "daily-tech",
                "web", "user-1", SessionStatus.ACTIVE,
                Instant.parse("2026-11-04T00:00:00Z"),
                Instant.parse("2026-11-04T00:01:00Z"), 4, "今日科技速递");
        when(sessions.listRecent(100, Optional.of(SessionStatus.ACTIVE))).thenReturn(List.of(summary));

        assertThat(service.list("active")).containsExactly(SessionSummaryView.from(summary));
        verify(sessions).listRecent(100, Optional.of(SessionStatus.ACTIVE));
        assertThatThrownBy(() -> service.list("archived"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void rejectsCompetingOperationsOnOneSessionButAllowsAnotherSession() throws Exception {
        var first = new Session("web", "user-1", "daily-tech");
        var second = new Session("web", "user-2", "daily-tech");
        when(sessions.find(first.id())).thenReturn(Optional.of(first));
        when(sessions.find(second.id())).thenReturn(Optional.of(second));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(agents.process(first, "hold")).thenAnswer(invocation -> {
            entered.countDown();
            release.await(1, TimeUnit.SECONDS);
            return "first";
        });
        when(agents.process(second, "go")).thenReturn("second");

        var firstCall = CompletableFuture.supplyAsync(() -> service.send(first.id(), "hold"));
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertBusy(() -> service.send(first.id(), "again"));
        assertBusy(() -> service.archive(first.id()));
        assertThat(service.send(second.id(), "go").reply()).isEqualTo("second");

        release.countDown();
        assertThat(firstCall.get(1, TimeUnit.SECONDS).reply()).isEqualTo("first");
        verify(agents, never()).process(first, "again");
    }

    @Test
    void restoresArchivedSessionAndRejectsNonArchived() {
        var archived = new Session("web", "user-1", "daily-tech");
        var archivedDetails = new SessionDetails(archived.id(), "web", "user-1", "daily-tech",
                SessionStatus.ARCHIVED, archived.createdAt(), archived.lastActiveAt(),
                archived.lastActiveAt().plusSeconds(60), archived.messages());
        var activeDetails = details(archived);
        // 第一次 details 返回归档态（校验），restore 内部 get() 再读时返回恢复后的活动态
        when(sessions.details(archived.id()))
                .thenReturn(Optional.of(archivedDetails), Optional.of(activeDetails));
        when(sessions.restore(archived.id())).thenReturn(true);

        var view = service.restore(archived.id());

        assertThat(view.id()).isEqualTo(archived.id());
        assertThat(view.status()).isEqualTo(SessionStatus.ACTIVE);
        verify(sessions).restore(archived.id());

        var active = new Session("cli", "user-2", "daily-tech");
        when(sessions.details(active.id())).thenReturn(Optional.of(details(active)));
        assertThatThrownBy(() -> service.restore(active.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("SESSION_NOT_ARCHIVED"));
        verify(sessions, never()).restore(active.id());

        when(sessions.details("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.restore("missing"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("SESSION_NOT_FOUND"));
    }

    @Test
    void renamesSessionAcrossChannelsAndRejectsBlankOrMissing() {
        var archived = new Session("cli", "user-1", "daily-tech");
        when(sessions.details(archived.id())).thenReturn(Optional.of(details(archived)));
        when(sessions.rename(archived.id(), "每日科技")).thenReturn(true);

        var view = service.rename(archived.id(), "  每日科技  ");

        assertThat(view.sessionId()).isEqualTo(archived.id());
        assertThat(view.title()).isEqualTo("每日科技");
        verify(sessions).rename(archived.id(), "每日科技");

        assertThatThrownBy(() -> service.rename(archived.id(), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("会话名不能为空");
        assertThatThrownBy(() -> service.rename(archived.id(), "a".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");

        when(sessions.details("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rename("missing", "名字"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("SESSION_NOT_FOUND"));
    }

    private static void assertBusy(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.code()).isEqualTo("SESSION_BUSY"));
    }

    private static SessionDetails details(Session session) {
        return new SessionDetails(session.id(), session.channel(), session.userId(),
                session.profileName(), SessionStatus.ACTIVE, session.createdAt(),
                session.lastActiveAt(), null, session.messages());
    }

    private static Profile profile(String name) {
        return new Profile(name, "测试", new Profile.Identity(name, "测试"),
                new Profile.ProviderRef("test", "test", null), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(3, 10), Path.of(name, "AGENT.md"));
    }
}
