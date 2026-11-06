package io.oryxos.web.agent;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.web.api.AgentCallExecutor;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.SessionStorageException;
import io.oryxos.web.api.Utf8MessageValidator;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 为每次外部调用创建独立内部 Session，并复用 AgentService 主链路。 */
@Service
public final class AgentInvocationWebService {

    private final ProfileRegistry profiles;

    private final SessionManager sessions;

    private final AgentService agents;

    private final AgentCallExecutor calls;

    private final Utf8MessageValidator messages;

    public AgentInvocationWebService(ProfileRegistry profiles, SessionManager sessions,
            AgentService agents, AgentCallExecutor calls, Utf8MessageValidator messages) {
        this.profiles = Objects.requireNonNull(profiles);
        this.sessions = Objects.requireNonNull(sessions);
        this.agents = Objects.requireNonNull(agents);
        this.calls = Objects.requireNonNull(calls);
        this.messages = Objects.requireNonNull(messages);
    }

    public InvokeAgentView invoke(String agentName, String message) {
        String normalizedName = required(agentName);
        if (profiles.find(normalizedName).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在");
        }
        String normalizedMessage = messages.validate(message);
        Session session;
        try {
            session = sessions.getOrCreate("web-invoke", UUID.randomUUID().toString(), normalizedName);
        }
        catch (RuntimeException exception) {
            throw new SessionStorageException("一次性调用会话创建失败", exception);
        }
        return new InvokeAgentView(calls.execute(() -> agents.process(session, normalizedMessage)));
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent 名称不能为空");
        }
        return value.trim();
    }
}
