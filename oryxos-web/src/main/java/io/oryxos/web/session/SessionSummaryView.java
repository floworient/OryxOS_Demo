package io.oryxos.web.session;

import io.oryxos.core.session.SessionStatus;
import io.oryxos.core.session.SessionSummary;
import java.time.Instant;

/** REST 会话列表的安全摘要，不包含消息正文；title 为会话名，可为 null。 */
public record SessionSummaryView(String id, String profileName, String channel, String userId,
        SessionStatus status, Instant createdAt, Instant lastActiveAt, int messageCount,
        String title) {

    public static SessionSummaryView from(SessionSummary summary) {
        return new SessionSummaryView(summary.id(), summary.profileName(), summary.channel(),
                summary.userId(), summary.status(), summary.createdAt(), summary.lastActiveAt(),
                summary.messageCount(), summary.title());
    }
}
