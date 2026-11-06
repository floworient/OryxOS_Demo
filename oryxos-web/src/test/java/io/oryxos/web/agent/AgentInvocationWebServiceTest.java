package io.oryxos.web.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.web.api.AgentCallExecutor;
import io.oryxos.web.api.AgentCallTimeoutException;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.Utf8MessageValidator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentInvocationWebServiceTest {

    private final ProfileRegistry profiles = new ProfileRegistry();

    private final SessionManager sessions = mock(SessionManager.class);

    private final AgentService agents = mock(AgentService.class);

    private final java.util.concurrent.ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    private AgentInvocationWebService service;

    @BeforeEach
    void setUp() {
        profiles.register(profile("daily-tech"));
        service = service(Duration.ofSeconds(1));
    }

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void createsIndependentInternalSessionsAndReturnsOnlyReply() {
        var first = new Session("web-invoke", "random-1", "daily-tech");
        var second = new Session("web-invoke", "random-2", "daily-tech");
        when(sessions.getOrCreate(eq("web-invoke"), anyString(), eq("daily-tech")))
                .thenReturn(first, second);
        when(agents.process(first, "hello")).thenReturn("reply-1");
        when(agents.process(second, "hello")).thenReturn("reply-2");

        assertThat(service.invoke("daily-tech", " hello "))
                .isEqualTo(new InvokeAgentView("reply-1"));
        assertThat(service.invoke("daily-tech", "hello"))
                .isEqualTo(new InvokeAgentView("reply-2"));
        assertThat(first.id()).isNotEqualTo(second.id());
        var order = inOrder(sessions, agents);
        order.verify(sessions).getOrCreate(eq("web-invoke"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("daily-tech"));
        order.verify(agents).process(first, "hello");
    }

    @Test
    void rejectsUnknownAgentBeforeCreatingSession() {
        assertThatThrownBy(() -> service.invoke("missing", "hello"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("AGENT_NOT_FOUND"));
        verify(sessions, never()).getOrCreate(
                org.mockito.ArgumentMatchers.anyString(), anyString(), anyString());
    }

    @Test
    void keepsCreatedSessionWhenAgentFails() {
        var session = new Session("web-invoke", "random-1", "daily-tech");
        when(sessions.getOrCreate(eq("web-invoke"), anyString(), eq("daily-tech")))
                .thenReturn(session);
        when(agents.process(session, "hello")).thenThrow(new IllegalStateException("failure"));

        assertThatThrownBy(() -> service.invoke("daily-tech", "hello"))
                .isInstanceOf(IllegalStateException.class);
        verify(sessions).getOrCreate(eq("web-invoke"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("daily-tech"));
    }

    @Test
    void enforcesConfiguredDeadlineAndInterruptsAgentCall() throws Exception {
        var session = new Session("web-invoke", "random-1", "daily-tech");
        var interrupted = new CountDownLatch(1);
        when(sessions.getOrCreate(eq("web-invoke"), anyString(), eq("daily-tech")))
                .thenReturn(session);
        when(agents.process(session, "hello")).thenAnswer(invocation -> {
            try {
                Thread.sleep(Duration.ofSeconds(30));
            }
            catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
            }
            return "late";
        });
        service = service(Duration.ofMillis(50));

        assertThatThrownBy(() -> service.invoke("daily-tech", "hello"))
                .isInstanceOf(AgentCallTimeoutException.class);
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    private AgentInvocationWebService service(Duration timeout) {
        return new AgentInvocationWebService(profiles, sessions, agents,
                new AgentCallExecutor(executor, timeout), new Utf8MessageValidator());
    }

    private static Profile profile(String name) {
        return new Profile(name, "测试", new Profile.Identity(name, "测试"),
                new Profile.ProviderRef("test", "test", null), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(3, 10), Path.of(name, "AGENT.md"));
    }
}
