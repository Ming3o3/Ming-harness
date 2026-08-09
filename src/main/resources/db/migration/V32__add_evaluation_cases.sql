CREATE TABLE harness_evaluation_cases (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    owner_user_id VARCHAR(128) NOT NULL,
    source_run_id VARCHAR(128),
    name VARCHAR(200) NOT NULL,
    input TEXT NOT NULL,
    tool_name VARCHAR(128),
    expected_contains TEXT,
    budget NUMERIC(20, 6),
    scenario VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_evaluation_cases PRIMARY KEY (id)
);

CREATE INDEX idx_harness_evaluation_cases_tenant_created
    ON harness_evaluation_cases (tenant_id, created_at DESC);
