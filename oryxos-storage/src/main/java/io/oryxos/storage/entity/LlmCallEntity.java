package io.oryxos.storage.entity;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "llm_calls")
public class LlmCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "profile_name", nullable = false)
    private String profileName;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "usage_known", nullable = false)
    @Convert(converter = NumericBooleanConverter.class)
    private boolean usageKnown;

    @Column(name = "duration_ms", nullable = false)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LlmCallStatus status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LlmCallEntity() {
    }

    public LlmCallEntity(LlmCallRecord record) {
        this.id = record.id();
        this.sessionId = record.sessionId();
        this.profileName = record.profileName();
        this.provider = record.provider();
        this.model = record.model();
        this.promptTokens = record.promptTokens();
        this.completionTokens = record.completionTokens();
        this.totalTokens = record.totalTokens();
        this.usageKnown = record.usageKnown();
        this.durationMs = record.durationMs();
        this.status = record.status();
        this.errorCode = record.errorCode();
        this.errorMessage = record.errorMessage();
        this.createdAt = record.createdAt();
    }

    public LlmCallRecord toRecord() {
        return new LlmCallRecord(id, sessionId, profileName, provider, model, promptTokens, completionTokens,
                totalTokens, usageKnown, durationMs, status, errorCode, errorMessage, createdAt);
    }
}
