-- 用户可在控制台配置自己的 OpenAI 兼容模型供应商；API Key 只保存 AES-GCM 密文。
CREATE TABLE harness_model_provider_configs (
    id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    api_key_ciphertext TEXT,
    api_key_hint VARCHAR(32),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_model_provider_configs PRIMARY KEY (id),
    CONSTRAINT uk_harness_model_provider_configs_owner UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_harness_model_provider_configs_owner_updated
    ON harness_model_provider_configs (tenant_id, user_id, updated_at DESC);
