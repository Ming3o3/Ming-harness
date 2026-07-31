package org.mingharness.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * API Key 持久化凭证，只保存 SHA-256 摘要，数据库和日志中永远不出现可用明文密钥。
 */
@Entity
@Table(name = "harness_api_keys", indexes = {
        @Index(name = "idx_harness_api_keys_tenant_created", columnList = "tenant_id, created_at"),
        @Index(name = "idx_harness_api_keys_hash", columnList = "key_hash", unique = true)
})
public class ApiKeyCredential {

    @Id
    private String id;

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 2000)
    private String permissions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApiKeyStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 255)
    private String revokedBy;

    @Version
    private long version;

    protected ApiKeyCredential() {
    }

    public ApiKeyCredential(String keyHash, String keyPrefix, String tenantId, String userId,
                            String permissions, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.tenantId = tenantId;
        this.userId = userId;
        this.permissions = permissions;
        this.status = ApiKeyStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public void revoke(String actorId) {
        if (status == ApiKeyStatus.REVOKED) {
            return;
        }
        this.status = ApiKeyStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revokedBy = actorId;
    }

    public String getId() { return id; }
    public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getPermissions() { return permissions; }
    public ApiKeyStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public long getVersion() { return version; }

    public boolean usableAt(Instant now) {
        return status == ApiKeyStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }
}
