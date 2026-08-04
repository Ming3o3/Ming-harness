package org.mingharness.runtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 记录组织资源策略的创建、修改和恢复默认操作，便于运维追溯配置漂移。 */
@Entity
@Table(name = "harness_tenant_policy_audits")
public class TenantPolicyAudit {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TenantPolicyAudit() {
    }

    public TenantPolicyAudit(String tenantId, String actorId, String eventType, String details) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getEventType() { return eventType; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
