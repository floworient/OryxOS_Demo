package io.oryxos.storage.entity;

import io.oryxos.core.audit.ToolInvocationRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tool_invocations")
public class ToolInvocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Column(name = "input_json", nullable = false, columnDefinition = "TEXT")
    private String inputJson;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(nullable = false)
    @Convert(converter = NumericBooleanConverter.class)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ToolInvocation() {
    }

    public ToolInvocation(ToolInvocationRecord record) {
        this.sessionId = record.sessionId();
        this.toolName = record.toolName();
        this.inputJson = record.inputJson();
        this.resultJson = record.resultJson();
        this.success = record.success();
        this.errorMessage = record.errorMessage();
        this.durationMs = record.durationMs();
        this.createdAt = record.createdAt();
    }
}
