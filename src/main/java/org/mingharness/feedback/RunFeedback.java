package org.mingharness.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "harness_run_feedback", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_run_feedback_run_user", columnNames = {"run_id", "user_id"}))
public class RunFeedback {

    @Id
    private String id;
    @Column(name = "run_id", nullable = false, length = 128)
    private String runId;
    @Column(name = "message_id", length = 128)
    private String messageId;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(nullable = false, length = 16)
    private String rating;
    @Column(name = "reason_code", length = 64)
    private String reasonCode;
    @Column(columnDefinition = "text")
    private String note;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RunFeedback() {
    }

    public RunFeedback(String runId, String messageId, String tenantId, String userId,
                       String rating, String reasonCode, String note) {
        this.id = UUID.randomUUID().toString();
        this.runId = runId;
        this.messageId = messageId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.rating = rating;
        this.reasonCode = reasonCode;
        this.note = note;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String messageId, String rating, String reasonCode, String note) {
        this.messageId = messageId;
        this.rating = rating;
        this.reasonCode = reasonCode;
        this.note = note;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getMessageId() { return messageId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getRating() { return rating; }
    public String getReasonCode() { return reasonCode; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
