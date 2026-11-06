package io.oryxos.web.session;

import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionDetails;
import io.oryxos.core.session.SessionStatus;
import java.time.Instant;
import java.util.List;

/** 会话身份、状态和受限历史视图。 */
public record SessionView(String id, String channel, String userId, String profileName,
        SessionStatus status, Instant createdAt, Instant lastActiveAt, Instant archivedAt,
        boolean historyTruncated, List<MessageView> messages) {

    private static final int MAX_HISTORY = 100;

    public SessionView {
        messages = List.copyOf(messages);
    }

    static SessionView from(SessionDetails details) {
        int start = Math.max(0, details.messages().size() - MAX_HISTORY);
        return new SessionView(details.id(), details.channel(), details.userId(),
                details.profileName(), details.status(), details.createdAt(), details.lastActiveAt(),
                details.archivedAt(), start > 0,
                details.messages().subList(start, details.messages().size()).stream()
                        .map(MessageView::from).toList());
    }

    static SessionView active(Session session) {
        return from(new SessionDetails(session.id(), session.channel(), session.userId(),
                session.profileName(), SessionStatus.ACTIVE, session.createdAt(),
                session.lastActiveAt(), null, session.messages()));
    }
}
