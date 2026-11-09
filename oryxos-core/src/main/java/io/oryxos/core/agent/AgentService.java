package io.oryxos.core.agent;

import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import java.util.function.BooleanSupplier;

public final class AgentService {

    private static final ThreadLocal<BooleanSupplier> CANCELLATION = new ThreadLocal<>();

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
            ReActLoop.checkInterrupted();
            sessions.save(session);
            return reply;
        }
        finally {
            ProfileContext.clear();
        }
    }

    /** 为受控触发绑定独立于线程中断标记的取消状态。 */
    public static void withCancellation(BooleanSupplier cancelled, Runnable action) {
        BooleanSupplier previous = CANCELLATION.get();
        CANCELLATION.set(cancelled);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CANCELLATION.remove();
            } else {
                CANCELLATION.set(previous);
            }
        }
    }

    static BooleanSupplier cancellation() {
        return CANCELLATION.get();
    }
}
