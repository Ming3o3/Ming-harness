package org.mingharness.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 事务 Outbox 记录，保证数据库状态和执行命令不会出现单边提交。 */
@Entity
@Table(name = "harness_outbox_events")
public class OutboxEvent {

    @Id
    private String id;
    @Column(nullable = false)
    private String runId;
    @Column(nullable = false)
    private String tenantId;
    private String traceId;
    @Column(nullable = false)
    private String command;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    private Instant nextAttemptAt;
    private Instant createdAt;
    private Instant publishedAt;
    @Column(columnDefinition = "text")
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(String runId, String tenantId, String traceId, String command, String payload) {
        this.id = UUID.randomUUID().toString();
        this.runId = runId;
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.command = command;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error;
        this.nextAttemptAt = Instant.now().plusSeconds(Math.min(60, Math.max(1, attempts * 2L)));
        if (attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        }
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getTenantId() { return tenantId; }
    public String getTraceId() { return traceId; }
    public String getCommand() { return command; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}
