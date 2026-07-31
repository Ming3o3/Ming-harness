package org.mingharness.audit;

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
}
