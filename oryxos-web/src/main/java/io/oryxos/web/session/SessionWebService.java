package io.oryxos.web.session;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionDetails;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.session.SessionStatus;
import io.oryxos.web.api.AgentCallExecutor;
import io.oryxos.web.api.ApiException;
import io.oryxos.web.api.SessionStorageException;
import io.oryxos.web.api.Utf8MessageValidator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 连续 Web Session 的应用服务；所有 Agent 执行仍统一进入 AgentService。 */
@Service
public final class SessionWebService {

    private final ProfileRegistry profiles;

    private final SessionManager sessions;

    private final AgentService agents;

    private final AgentCallExecutor calls;

    private final Utf8MessageValidator messages;

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public SessionWebService(ProfileRegistry profiles, SessionManager sessions, AgentService agents,
            AgentCallExecutor calls, Utf8MessageValidator messages) {
        this.profiles = Objects.requireNonNull(profiles);
        this.sessions = Objects.requireNonNull(sessions);
        this.agents = Objects.requireNonNull(agents);
        this.calls = Objects.requireNonNull(calls);
        this.messages = Objects.requireNonNull(messages);
    }

    public Creation create(String profileName, String userId) {
        String normalizedProfile = required(profileName, "Agent 名称不能为空");
        String normalizedUser = required(userId, "用户标识不能为空");
        if (normalizedUser.length() > 255) {
            throw new IllegalArgumentException("用户标识不能超过 255 个字符");
        }
        if (profiles.find(normalizedProfile).isEmpty()) {
            throw notFound("AGENT_NOT_FOUND", "Agent 不存在");
        }
        var acquired = storage(() -> sessions.acquire("web", normalizedUser, normalizedProfile));
        Session session = storage(() -> sessions.find(acquired.sessionId())).orElseThrow(
                () -> new SessionStorageException("新建会话不可见", null));
        return new Creation(SessionView.active(session), acquired.created());
    }

    public MessageReplyView send(String sessionId, String message) {
        String normalized = messages.validate(message);
        ReentrantLock lock = lock(sessionId);
        if (!lock.tryLock()) {
            throw conflict("SESSION_BUSY", "会话正在处理其他操作");
        }
        try {
            Session session = storage(() -> sessions.find(sessionId)).orElseThrow(
                    () -> notFound("SESSION_NOT_FOUND", "会话不存在"));
            if (!"web".equals(session.channel())) {
                throw conflict("SESSION_CHANNEL_NOT_WRITABLE", "该渠道会话不可通过 Web 写入");
            }
            String reply = calls.execute(() -> agents.process(session, normalized));
            return new MessageReplyView(session.id(), reply);
        }
        finally {
            lock.unlock();
        }
    }

    public SessionView get(String sessionId) {
        return SessionView.from(storage(() -> sessions.details(sessionId)).orElseThrow(
                () -> notFound("SESSION_NOT_FOUND", "会话不存在")));
    }

    public SessionArchiveView archive(String sessionId) {
        ReentrantLock lock = lock(sessionId);
        if (!lock.tryLock()) {
            throw conflict("SESSION_BUSY", "会话正在处理其他操作");
        }
        try {
            SessionDetails details = storage(() -> sessions.details(sessionId)).orElseThrow(
                    () -> notFound("SESSION_NOT_FOUND", "会话不存在"));
            if (details.status() != SessionStatus.ACTIVE) {
                throw notFound("SESSION_NOT_FOUND", "会话不存在");
            }
            if (!"web".equals(details.channel())) {
                throw conflict("SESSION_CHANNEL_NOT_WRITABLE", "该渠道会话不可通过 Web 归档");
            }
            if (!storage(() -> sessions.archive(sessionId))) {
                throw notFound("SESSION_NOT_FOUND", "会话不存在");
            }
            return new SessionArchiveView(sessionId, SessionStatus.ARCHIVED);
        }
        finally {
            lock.unlock();
        }
    }

    private ReentrantLock lock(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw notFound("SESSION_NOT_FOUND", "会话不存在");
        }
        return locks.computeIfAbsent(sessionId, ignored -> new ReentrantLock());
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static <T> T storage(Supplier<T> operation) {
        try {
            return operation.get();
        }
        catch (RuntimeException exception) {
            throw new SessionStorageException("会话存储操作失败", exception);
        }
    }

    public record Creation(SessionView session, boolean created) { }
}
