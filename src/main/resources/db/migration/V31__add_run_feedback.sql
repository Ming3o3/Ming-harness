CREATE TABLE harness_run_feedback (
    id VARCHAR(255) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(128),
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    rating VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64),
    note TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_run_feedback PRIMARY KEY (id),
    CONSTRAINT uk_harness_run_feedback_run_user UNIQUE (run_id, user_id),
    CONSTRAINT fk_harness_run_feedback_run FOREIGN KEY (run_id) REFERENCES harness_runs(id) ON DELETE CASCADE
);

CREATE INDEX idx_harness_run_feedback_tenant_created
    ON harness_run_feedback (tenant_id, created_at DESC);
