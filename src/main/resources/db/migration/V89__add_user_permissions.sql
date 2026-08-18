-- 租户内用户直接授权；与 API Key 权限合并，支持不轮换密钥即时变更权限。
CREATE TABLE harness_user_permissions (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    permissions VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_user_permissions PRIMARY KEY (id),
    CONSTRAINT uk_harness_user_permissions_identity UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_harness_user_permissions_tenant_updated
    ON harness_user_permissions (tenant_id, updated_at DESC);
