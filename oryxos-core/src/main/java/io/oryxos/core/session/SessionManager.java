package io.oryxos.core.session;

import java.util.Optional;

public interface SessionManager {

    void save(Session session);

    Optional<Session> find(String sessionId);
}
