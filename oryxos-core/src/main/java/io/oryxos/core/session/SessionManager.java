package io.oryxos.core.session;

import java.util.List;
import java.util.Optional;

public interface SessionManager {

    Session getOrCreate(String channel, String userId, String profileName);

    default SessionAcquisition acquire(String channel, String userId, String profileName) {
        return new SessionAcquisition(getOrCreate(channel, userId, profileName).id(), false);
    }

    void save(Session session);

    Optional<Session> find(String sessionId);

    Optional<SessionDetails> details(String sessionId);

    boolean archive(String sessionId);

    List<SessionSummary> list();

    default List<SessionSummary> listRecent(int limit, Optional<SessionStatus> status) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("会话列表上限必须在 1 到 100 之间");
        }
        return list().stream()
                .filter(item -> status.isEmpty() || item.status() == status.orElseThrow())
                .limit(limit)
                .toList();
    }
}
