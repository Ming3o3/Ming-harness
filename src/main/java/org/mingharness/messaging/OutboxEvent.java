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
    private String claimedBy;
    private Instant claimUntil;
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

    /**
     * 由持有发布租约的 Relay 确认事件已发送。
     *
     * @return 当前 Relay 是否仍然持有租约，避免过期 Worker 覆盖新 Worker 的处理结果。
     */
    public boolean markPublished(String relayId) {
        if (!isClaimedBy(relayId)) {
            return false;
        }
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
        clearClaim();
        return true;
    }

    /**
     * 由持有发布租约的 Relay 记录一次发布失败。
     *
     * @return 当前 Relay 是否仍然持有租约，租约已被其他实例接管时不修改事件状态。
     */
    public boolean markFailed(String relayId, String error, int maxAttempts) {
        if (!isClaimedBy(relayId)) {
            return false;
        }
        this.attempts++;
        this.lastError = error;
        this.nextAttemptAt = Instant.now().plusSeconds(Math.min(60, Math.max(1, attempts * 2L)));
        if (attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING;
        }
        clearClaim();
        return true;
    }

    /**
     * 在数据库行锁保护下抢占待发布事件。若上一实例崩溃，过期租约允许安全接管。
     */
    public boolean claim(String relayId, Instant now, Instant leaseUntil) {
        if (relayId == null || relayId.isBlank() || now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("Outbox 发布租约参数不合法");
        }
        boolean pendingAndDue = status == OutboxStatus.PENDING
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
        boolean expiredClaim = status == OutboxStatus.PUBLISHING
                && (claimUntil == null || !claimUntil.isAfter(now));
        if (!pendingAndDue && !expiredClaim) {
            return false;
        }
        this.status = OutboxStatus.PUBLISHING;
        this.claimedBy = relayId;
        this.claimUntil = leaseUntil;
        return true;
    }

    public boolean isClaimedBy(String relayId) {
        return status == OutboxStatus.PUBLISHING
                && relayId != null
                && relayId.equals(claimedBy);
    }

    private void clearClaim() {
        this.claimedBy = null;
        this.claimUntil = null;
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
    public String getClaimedBy() { return claimedBy; }
    public Instant getClaimUntil() { return claimUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}
