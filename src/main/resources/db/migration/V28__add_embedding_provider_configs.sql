-- 组织级 Embedding 供应商配置；API Key 只保存 AES-GCM 密文和掩码。
CREATE TABLE harness_embedding_provider_configs (
    id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    model_version VARCHAR(128) NOT NULL,
    dimension INTEGER NOT NULL,
    api_key_ciphertext TEXT,
    api_key_hint VARCHAR(32),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_embedding_provider_configs PRIMARY KEY (id),
    CONSTRAINT uk_harness_embedding_provider_configs_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_harness_embedding_provider_configs_dimension CHECK (dimension > 0)
);

CREATE INDEX idx_harness_embedding_provider_configs_tenant_updated
    ON harness_embedding_provider_configs (tenant_id, updated_at DESC);
