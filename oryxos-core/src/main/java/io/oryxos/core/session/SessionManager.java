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
}
