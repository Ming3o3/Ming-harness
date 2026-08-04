package org.mingharness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** 按组织和用户隔离的模型供应商配置；API Key 只保存密文和不可逆掩码。 */
@Entity
@Table(name = "harness_model_provider_configs", indexes = {
        @Index(name = "idx_harness_model_provider_configs_owner_updated",
                columnList = "tenant_id, user_id, updated_at")
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_model_provider_configs_owner",
        columnNames = {"tenant_id", "user_id"}
))
public class ModelProviderConfig {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "api_key_ciphertext", columnDefinition = "text")
    private String apiKeyCiphertext;

    @Column(name = "api_key_hint", length = 32)
    private String apiKeyHint;

    @Column(name = "active_snapshot_id", length = 128)
    private String activeSnapshotId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ModelProviderConfig() {
    }

    public ModelProviderConfig(String tenantId, String userId, boolean enabled,
                               String baseUrl, String modelName,
                               String apiKeyCiphertext, String apiKeyHint) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.createdAt = Instant.now();
        update(enabled, baseUrl, modelName, apiKeyCiphertext, apiKeyHint);
    }

    public void update(boolean enabled, String baseUrl, String modelName,
                       String apiKeyCiphertext, String apiKeyHint) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyHint = apiKeyHint;
        this.updatedAt = Instant.now();
    }

    public void attachSnapshot(String snapshotId) {
        this.activeSnapshotId = snapshotId;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public boolean isEnabled() { return enabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getModelName() { return modelName; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public String getApiKeyHint() { return apiKeyHint; }
    public String getActiveSnapshotId() { return activeSnapshotId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
