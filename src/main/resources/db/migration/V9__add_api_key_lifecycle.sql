-- 数据库 API Key 生命周期管理：仅保存摘要，支持过期与即时撤销。
CREATE TABLE harness_api_keys (
    id VARCHAR(255) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    permissions VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_by VARCHAR(255),
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_api_keys PRIMARY KEY (id),
    CONSTRAINT uk_harness_api_keys_hash UNIQUE (key_hash)
);

CREATE INDEX idx_harness_api_keys_tenant_created
    ON harness_api_keys (tenant_id, created_at DESC);

CREATE TABLE harness_api_key_audits (
    id VARCHAR(255) NOT NULL,
    key_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    details VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_api_key_audits PRIMARY KEY (id)
);

CREATE INDEX idx_harness_api_key_audits_key_created
    ON harness_api_key_audits (key_id, created_at DESC);
CREATE INDEX idx_harness_api_key_audits_tenant_created
    ON harness_api_key_audits (tenant_id, created_at DESC);
