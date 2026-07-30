package org.mingharness.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
    private String eventType;
    @Lob
    private String message;
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(String runId, String stepId, String eventType, String message) {
        this.id = UUID.randomUUID().toString();
        this.runId = runId;
        this.stepId = stepId;
        this.eventType = eventType;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getStepId() { return stepId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
