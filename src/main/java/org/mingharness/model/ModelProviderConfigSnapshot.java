package org.mingharness.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Run 创建时使用的模型供应商快照；API Key 仍只保存 AES-GCM 密文。 */
@Entity
@Table(name = "harness_model_provider_config_snapshots", indexes = {
        @Index(name = "idx_harness_model_provider_config_snapshots_owner_created",
                columnList = "tenant_id, user_id, created_at")
})
public class ModelProviderConfigSnapshot {

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModelProviderConfigSnapshot() {
    }

    public ModelProviderConfigSnapshot(ModelProviderConfig config) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = config.getTenantId();
        this.userId = config.getUserId();
        this.enabled = config.isEnabled();
        this.baseUrl = config.getBaseUrl();
        this.modelName = config.getModelName();
        this.apiKeyCiphertext = config.getApiKeyCiphertext();
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public boolean isEnabled() { return enabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getModelName() { return modelName; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public Instant getCreatedAt() { return createdAt; }
}
