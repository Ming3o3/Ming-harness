-- Harness PostgreSQL 基线表结构。
-- 所有时间统一使用带时区时间，应用层以 UTC 读写。

CREATE TABLE harness_runs (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    title VARCHAR(255),
    model_name VARCHAR(255),
    prompt_version VARCHAR(255),
    policy_version VARCHAR(255),
    idempotency_key VARCHAR(128),
    permissions_snapshot TEXT,
    trace_id VARCHAR(255),
    input_data TEXT,
    output_data TEXT,
    error TEXT,
    status VARCHAR(32),
    budget NUMERIC(38, 2),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_runs PRIMARY KEY (id),
    CONSTRAINT uk_harness_run_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE harness_steps (
    id VARCHAR(255) NOT NULL,
    span_id VARCHAR(255),
    run_id VARCHAR(255) NOT NULL,
    step_sequence INTEGER,
    type VARCHAR(32),
    status VARCHAR(32),
    name VARCHAR(255),
    input_data TEXT,
    output_data TEXT,
    error TEXT,
    attempt INTEGER NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    approval_granted BOOLEAN NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    duration_ms BIGINT NOT NULL,
    cost NUMERIC(38, 2),
    CONSTRAINT pk_harness_steps PRIMARY KEY (id),
    CONSTRAINT fk_harness_steps_run FOREIGN KEY (run_id) REFERENCES harness_runs (id)
);

CREATE TABLE harness_audit_events (
    id VARCHAR(255) NOT NULL,
    run_id VARCHAR(255),
    step_id VARCHAR(255),
    tenant_id VARCHAR(255),
    actor_id VARCHAR(255),
    trace_id VARCHAR(255),
    event_type VARCHAR(255),
    message TEXT,
    metadata TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_audit_events PRIMARY KEY (id)
);

CREATE TABLE harness_context_documents (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    owner_user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    sensitivity VARCHAR(255) NOT NULL,
    allowed_users VARCHAR(2000),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_context_documents PRIMARY KEY (id)
);

CREATE TABLE harness_context_memories (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    memory_type VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    source_run_id VARCHAR(255),
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_context_memories PRIMARY KEY (id)
);

CREATE TABLE harness_evaluation_reports (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    model_name VARCHAR(255),
    prompt_version VARCHAR(255),
    policy_version VARCHAR(255),
    total_cases INTEGER NOT NULL,
    passed_cases INTEGER NOT NULL,
    failed_cases INTEGER NOT NULL,
    success_rate NUMERIC(38, 2),
    details TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_evaluation_reports PRIMARY KEY (id)
);

CREATE INDEX idx_harness_runs_tenant_created
    ON harness_runs (tenant_id, created_at DESC);
CREATE INDEX idx_harness_runs_status_updated
    ON harness_runs (status, updated_at);
CREATE INDEX idx_harness_steps_run_sequence
    ON harness_steps (run_id, step_sequence);
CREATE INDEX idx_harness_audit_run_created
    ON harness_audit_events (run_id, created_at DESC);
CREATE INDEX idx_harness_audit_tenant_created
    ON harness_audit_events (tenant_id, created_at DESC);
CREATE INDEX idx_harness_documents_tenant_updated
    ON harness_context_documents (tenant_id, updated_at DESC);
CREATE INDEX idx_harness_memories_tenant_user_created
    ON harness_context_memories (tenant_id, user_id, created_at DESC);
CREATE INDEX idx_harness_evaluation_tenant_created
    ON harness_evaluation_reports (tenant_id, created_at DESC);
