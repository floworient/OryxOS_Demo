package io.oryxos.core.session;

import java.util.List;
import java.util.Optional;

public interface SessionManager {

    Session getOrCreate(String channel, String userId, String profileName);

    void save(Session session);

    Optional<Session> find(String sessionId);

    List<SessionSummary> list();
}
