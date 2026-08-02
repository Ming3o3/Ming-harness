-- Run 创建时固化模型供应商配置，避免用户更新设置后影响正在排队、审批或执行中的 Run。
ALTER TABLE harness_model_provider_configs
    ADD COLUMN IF NOT EXISTS active_snapshot_id VARCHAR(128);

ALTER TABLE harness_runs
    ADD COLUMN IF NOT EXISTS model_config_snapshot_id VARCHAR(128);

CREATE TABLE harness_model_provider_config_snapshots (
    id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    api_key_ciphertext TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_model_provider_config_snapshots PRIMARY KEY (id)
);

CREATE INDEX idx_harness_model_provider_config_snapshots_owner_created
    ON harness_model_provider_config_snapshots (tenant_id, user_id, created_at DESC);

CREATE INDEX idx_harness_model_provider_configs_active_snapshot
    ON harness_model_provider_configs (active_snapshot_id);

ALTER TABLE harness_model_provider_configs
    ADD CONSTRAINT fk_harness_model_provider_configs_active_snapshot
    FOREIGN KEY (active_snapshot_id)
    REFERENCES harness_model_provider_config_snapshots (id);

ALTER TABLE harness_runs
    ADD CONSTRAINT fk_harness_runs_model_config_snapshot
    FOREIGN KEY (model_config_snapshot_id)
    REFERENCES harness_model_provider_config_snapshots (id);

CREATE INDEX idx_harness_runs_model_config_snapshot
    ON harness_runs (model_config_snapshot_id);
