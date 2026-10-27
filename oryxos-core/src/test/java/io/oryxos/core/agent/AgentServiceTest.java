package io.oryxos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

    @Test
    void savesSuccessfulAndLimitedRepliesAndAlwaysClearsContext() {
        var profile = mock(Profile.class);
        when(profile.name()).thenReturn("weather");
        var registry = new ProfileRegistry();
        registry.register(profile);
        var loop = mock(ReActLoop.class);
        var store = mock(SessionManager.class);
        var session = new Session("cli", "u", "weather");
        var service = new AgentService(registry, loop, store);
        when(loop.run(any(), any(), any())).thenAnswer(call -> {
            assertThat(ProfileContext.current()).isSameAs(profile);
            return "达到最大轮数，已停止";
        });
        assertThat(service.process(session, "hi")).contains("达到最大轮数");
        verify(store).save(session);
        assertThat(ProfileContext.current()).isNull();
        doThrow(new IllegalStateException("boom")).when(loop).run(any(), any(), any());
        assertThatThrownBy(() -> service.process(session, "hi")).hasMessage("boom");
        assertThat(ProfileContext.current()).isNull();
    }

    @Test
    void persistenceAndUnknownAgentFailuresRemainVisible() {
        var profile = mock(Profile.class);
        when(profile.name()).thenReturn("weather");
        var registry = new ProfileRegistry();
        registry.register(profile);
        var store = mock(SessionManager.class);
        doThrow(new IllegalStateException("save failed")).when(store).save(any());
        var loop = mock(ReActLoop.class);
        when(loop.run(any(), any(), any())).thenReturn("done");
        var service = new AgentService(registry, loop, store);
        assertThatThrownBy(() -> service.process(new Session("cli", "u", "weather"), "hi"))
                .hasMessage("save failed");
        assertThat(ProfileContext.current()).isNull();
        assertThatThrownBy(() -> service.process(new Session("cli", "u", "unknown"), "hi"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown");
        assertThat(ProfileContext.current()).isNull();
    }
}
