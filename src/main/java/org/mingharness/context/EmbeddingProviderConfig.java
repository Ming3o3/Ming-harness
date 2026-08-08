package org.mingharness.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 组织级 embedding 供应商配置。
 *
 * <p>知识库 chunk 属于组织共享的向量空间，因此 embedding 配置也按组织隔离；
 * 控制台用户只是在当前组织内维护这份配置。API Key 只保存 AES-GCM 密文和掩码。</p>
 */
@Entity
@Table(name = "harness_embedding_provider_configs", indexes = {
        @Index(name = "idx_harness_embedding_provider_configs_tenant_updated",
                columnList = "tenant_id, updated_at")
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_harness_embedding_provider_configs_tenant",
        columnNames = "tenant_id"
))
public class EmbeddingProviderConfig {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 128)
    private String modelVersion;

    @Column(nullable = false)
    private int dimension;

    @Column(name = "api_key_ciphertext", columnDefinition = "text")
    private String apiKeyCiphertext;

    @Column(name = "api_key_hint", length = 32)
    private String apiKeyHint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected EmbeddingProviderConfig() {
    }

    public EmbeddingProviderConfig(String tenantId, boolean enabled, String baseUrl,
                                   String modelName, String modelVersion, int dimension,
                                   String apiKeyCiphertext, String apiKeyHint) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.createdAt = Instant.now();
        update(enabled, baseUrl, modelName, modelVersion, dimension, apiKeyCiphertext, apiKeyHint);
    }

    public void update(boolean enabled, String baseUrl, String modelName, String modelVersion,
                       int dimension, String apiKeyCiphertext, String apiKeyHint) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.dimension = dimension;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyHint = apiKeyHint;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public boolean isEnabled() { return enabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public int getDimension() { return dimension; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public String getApiKeyHint() { return apiKeyHint; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
