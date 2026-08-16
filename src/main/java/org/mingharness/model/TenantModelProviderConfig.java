package org.mingharness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** 租户级默认模型配置；管理员保存后，未设置个人覆盖的用户都会继承它。 */
@Entity
@Table(name = "harness_tenant_model_provider_configs", indexes = {
        @Index(name = "idx_harness_tenant_model_provider_configs_updated",
                columnList = "updated_at")
})
public class TenantModelProviderConfig {

    @Id
    private String tenantId;

    @Column(name = "config_id", nullable = false, unique = true, length = 128)
    private String configId;

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

    protected TenantModelProviderConfig() {
    }

    public TenantModelProviderConfig(String tenantId, boolean enabled, String baseUrl,
                                     String modelName, String apiKeyCiphertext, String apiKeyHint) {
        this.tenantId = tenantId;
        this.configId = UUID.randomUUID().toString();
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyHint = apiKeyHint;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
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
        this.updatedAt = Instant.now();
    }

    public String getTenantId() { return tenantId; }
    public String getConfigId() { return configId; }
    public boolean isEnabled() { return enabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getModelName() { return modelName; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public String getApiKeyHint() { return apiKeyHint; }
    public String getActiveSnapshotId() { return activeSnapshotId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
