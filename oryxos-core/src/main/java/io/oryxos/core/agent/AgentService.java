package io.oryxos.core.agent;

import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;

public final class AgentService {

    private final java.util.function.Function<String, java.util.Optional<Profile>> profiles;

    private final ReActLoop loop;

    private final SessionManager sessions;

    public AgentService(ProfileRegistry registry, ReActLoop loop, SessionManager sessions) {
        this.profiles = registry::find;
        this.loop = loop;
        this.sessions = sessions;
    }

    public String process(Session session, String userMessage) {
        try {
            var profile = profiles.apply(session.profileName()).orElseThrow(
                    () -> new IllegalArgumentException("未知 Agent: " + session.profileName()));
            ProfileContext.set(profile);
            String reply = loop.run(session, userMessage, profile);
            sessions.save(session);
            return reply;
        }
        finally {
            ProfileContext.clear();
        }
    }
}
