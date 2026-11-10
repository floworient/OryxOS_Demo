package io.oryxos.storage.entity;

import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionCompaction;
import io.oryxos.core.session.SessionDetails;
import io.oryxos.core.session.SessionSummary;
import io.oryxos.core.session.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.chat.messages.Message;

@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private String id;

    @Column(name = "profile_name", nullable = false, length = 100)
    private String profileName;

    @Column(nullable = false, length = 100)
    private String channel;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "messages_json", nullable = false, columnDefinition = "TEXT")
    private String messagesJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "context_summary", columnDefinition = "TEXT")
    private String contextSummary;

    @Column(name = "context_summary_until", nullable = false)
    private int contextSummaryUntil;

    protected SessionEntity() {
    }

    public SessionEntity(Session session, String messagesJson) {
        this.id = session.id();
        this.profileName = session.profileName();
        this.channel = session.channel();
        this.userId = session.userId();
        this.messagesJson = messagesJson;
        this.status = "active";
        this.createdAt = session.createdAt();
        this.lastActiveAt = session.lastActiveAt();
        this.messageCount = session.messages().size();
        session.compaction().ifPresent(value -> {
            this.contextSummary = value.text();
            this.contextSummaryUntil = value.processedUntil();
        });
    }

    public String messagesJson() {
        return messagesJson;
    }

    public String id() {
        return id;
    }

    public String status() {
        return status;
    }

    public void archive(Instant archivedAt) {
        if (!"active".equals(status)) {
            throw new IllegalStateException("只能归档活动会话");
        }
        this.status = "archived";
        this.archivedAt = java.util.Objects.requireNonNull(archivedAt);
    }

    public void unarchive() {
        if (!"archived".equals(status)) {
            throw new IllegalStateException("只能恢复已归档会话");
        }
        this.status = "active";
        this.archivedAt = null;
    }

    /** 设置会话名（持久层入口，允许 null 表示未命名）。 */
    public void title(String value) {
        this.title = value;
    }

    public String title() {
        return title;
    }

    public SessionSummary summary() {
        return new SessionSummary(id, profileName, channel, userId, sessionStatus(),
                createdAt, lastActiveAt, messageCount, title);
    }

    public Session restore(List<Message> messages) {
        if (!"active".equals(status)) {
            throw new IllegalStateException("本节不支持恢复已归档会话");
        }
        var session = new Session(id, channel, userId, profileName, createdAt, lastActiveAt, messages);
        if (contextSummary != null || contextSummaryUntil != 0) {
            session.updateCompaction(new SessionCompaction(contextSummary, contextSummaryUntil));
        }
        return session;
    }

    public SessionDetails details(List<Message> messages) {
        return new SessionDetails(id, channel, userId, profileName, sessionStatus(),
                createdAt, lastActiveAt, archivedAt, messages);
    }

    private SessionStatus sessionStatus() {
        return switch (status) {
            case "active" -> SessionStatus.ACTIVE;
            case "archived" -> SessionStatus.ARCHIVED;
            default -> throw new IllegalStateException("未知会话状态");
        };
    }
}
