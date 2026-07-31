-- 租户级资源治理：平台配置是硬上限，本表只保存每个租户更严格的运行限制。
CREATE TABLE harness_tenant_policies (
    tenant_id VARCHAR(255) NOT NULL,
    max_active_runs INTEGER NOT NULL CHECK (max_active_runs > 0),
    max_steps_per_run INTEGER NOT NULL CHECK (max_steps_per_run > 0),
    max_input_length INTEGER NOT NULL CHECK (max_input_length > 0),
    max_budget NUMERIC(38, 8) NOT NULL CHECK (max_budget > 0),
    max_creates_per_minute INTEGER NOT NULL CHECK (max_creates_per_minute > 0),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_tenant_policies PRIMARY KEY (tenant_id)
);

-- 策略变更单独留痕，便于排查租户限额为何在某个时间点发生变化。
CREATE TABLE harness_tenant_policy_audits (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_tenant_policy_audits PRIMARY KEY (id)
);

CREATE INDEX idx_harness_tenant_policy_audits_tenant_created
    ON harness_tenant_policy_audits (tenant_id, created_at DESC);
