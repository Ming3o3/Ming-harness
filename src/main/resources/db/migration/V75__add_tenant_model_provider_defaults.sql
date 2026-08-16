-- 管理员配置的租户级默认模型，供没有个人覆盖的教师和学生使用。
CREATE TABLE harness_tenant_model_provider_configs (
    tenant_id VARCHAR(128) NOT NULL,
    config_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    api_key_ciphertext TEXT,
    api_key_hint VARCHAR(32),
    active_snapshot_id VARCHAR(128),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_tenant_model_provider_configs PRIMARY KEY (tenant_id),
    CONSTRAINT uk_harness_tenant_model_provider_configs_id UNIQUE (config_id)
);

CREATE INDEX idx_harness_tenant_model_provider_configs_updated
    ON harness_tenant_model_provider_configs (updated_at DESC);

ALTER TABLE harness_model_provider_config_snapshots
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE harness_model_provider_config_snapshots
    ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'USER';

CREATE INDEX idx_harness_model_provider_config_snapshots_scope_created
    ON harness_model_provider_config_snapshots (tenant_id, scope, created_at DESC);

ALTER TABLE harness_tenant_model_provider_configs
    ADD CONSTRAINT fk_harness_tenant_model_provider_configs_snapshot
    FOREIGN KEY (active_snapshot_id)
    REFERENCES harness_model_provider_config_snapshots (id);
