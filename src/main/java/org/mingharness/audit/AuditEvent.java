package org.mingharness.audit;

import org.mingharness.common.SensitiveDataSanitizer;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "harness_audit_events")
public class AuditEvent {

    @Id
    private String id;
    private String runId;
    private String stepId;
    private String tenantId;
    private String actorId;
    private String traceId;
    private String eventType;
    @Column(columnDefinition = "text")
    private String message;
    @Column(columnDefinition = "text")
    private String metadata;
    private Instant createdAt;
    @Column(name = "integrity_sequence")
    private Long integritySequence;
    @Column(name = "previous_hash", length = 64)
    private String previousHash;
    @Column(name = "integrity_hash", length = 64)
    private String integrityHash;

    protected AuditEvent() {
    }

    public AuditEvent(String runId, String stepId, String eventType, String message) {
        this(null, null, null, runId, stepId, eventType, message, null);
    }

    public AuditEvent(String tenantId, String actorId, String traceId, String runId, String stepId,
                      String eventType, String message, String metadata) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.traceId = traceId;
        this.runId = runId;
        this.stepId = stepId;
        this.eventType = eventType;
        this.message = message;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    /** 事件写入前只能由审计服务完成一次封签。 */
    public void seal(long sequence, String previousHash, String integrityHash) {
        if (integritySequence != null || this.integrityHash != null) {
            throw new IllegalStateException("审计事件已经完成封签");
        }
        this.integritySequence = sequence;
        this.previousHash = previousHash;
        this.integrityHash = integrityHash;
    }

    /** 审计事件封签前统一脱敏，确保 HMAC 绑定的是安全后的持久化内容。 */
    public void sanitize(SensitiveDataSanitizer sanitizer) {
        if (sanitizer == null) {
            return;
        }
        this.message = sanitizer.sanitize(this.message);
        this.metadata = sanitizer.sanitize(this.metadata);
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getStepId() { return stepId; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getTraceId() { return traceId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public String getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getIntegritySequence() { return integritySequence; }
    public String getPreviousHash() { return previousHash; }
    public String getIntegrityHash() { return integrityHash; }
}
