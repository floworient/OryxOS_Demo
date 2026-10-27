package io.oryxos.storage.entity;

import io.oryxos.core.session.Session;
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
    }

    public String messagesJson() {
        return messagesJson;
    }

    public Session restore(List<Message> messages) {
        if (!"active".equals(status)) {
            throw new IllegalStateException("本节不支持恢复已归档会话");
        }
        var session = new Session(channel, userId, profileName, createdAt, lastActiveAt, messages);
        if (!session.id().equals(id)) {
            throw new IllegalStateException("会话身份与存储标识不一致");
        }
        return session;
    }
}
