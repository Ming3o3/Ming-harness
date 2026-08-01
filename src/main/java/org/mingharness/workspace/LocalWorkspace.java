package org.mingharness.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** 用户明确授权给本地 Agent 的代码目录；根目录只以密文形式持久化。 */
@Entity
@Table(name = "harness_local_workspaces", uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_local_workspace_owner_name",
        columnNames = {"tenant_id", "user_id", "display_name"}
))
public class LocalWorkspace {

    @Id
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;
    @Column(name = "root_path_ciphertext", nullable = false, columnDefinition = "text")
    private String rootPathCiphertext;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected LocalWorkspace() {
    }

    public LocalWorkspace(String tenantId, String userId, String displayName, String rootPathCiphertext) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.displayName = displayName;
        this.rootPathCiphertext = rootPathCiphertext;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** 用户再次授权同一个目录时仅刷新最近使用时间，保持会话绑定的工作区 ID 稳定。 */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getRootPathCiphertext() { return rootPathCiphertext; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
