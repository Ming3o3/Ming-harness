package org.mingharness.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** 租户内用户的直接授权；API Key 权限之外的增量授权也在这里持久化。 */
@Entity
@Table(name = "harness_user_permissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_harness_user_permissions_identity", columnNames = {"tenant_id", "user_id"})
}, indexes = {
        @Index(name = "idx_harness_user_permissions_tenant_updated", columnList = "tenant_id, updated_at")
})
public class HarnessUserPermission {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(nullable = false, length = 2000)
    private String permissions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @Version
    private long version;

    protected HarnessUserPermission() {
    }

    public HarnessUserPermission(String tenantId, String userId, String permissions, String updatedBy) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.permissions = permissions == null ? "" : permissions;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.updatedBy = updatedBy == null || updatedBy.isBlank() ? "system" : updatedBy;
    }

    public void update(String permissions, String updatedBy) {
        this.permissions = permissions == null ? "" : permissions;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy == null || updatedBy.isBlank() ? "system" : updatedBy;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getPermissions() { return permissions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }
}
