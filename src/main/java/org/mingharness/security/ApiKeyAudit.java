package org.mingharness.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** API Key 创建和撤销审计记录，不包含密钥明文或完整摘要。 */
@Entity
@Table(name = "harness_api_key_audits", indexes = {
        @Index(name = "idx_harness_api_key_audits_key_created", columnList = "key_id, created_at"),
        @Index(name = "idx_harness_api_key_audits_tenant_created", columnList = "tenant_id, created_at")
})
public class ApiKeyAudit {

    @Id
    private String id;

    @Column(name = "key_id", nullable = false, length = 255)
    private String keyId;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApiKeyAudit() {
    }

    public ApiKeyAudit(String keyId, String tenantId, String actorId, String eventType, String details) {
        this.id = UUID.randomUUID().toString();
        this.keyId = keyId;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getKeyId() { return keyId; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getEventType() { return eventType; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
