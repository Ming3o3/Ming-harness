-- 运行异步执行所需的 Worker 租约和事务 Outbox。
ALTER TABLE harness_runs ADD COLUMN IF NOT EXISTS worker_id VARCHAR(255);
ALTER TABLE harness_runs ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE harness_runs ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_harness_runs_lease
    ON harness_runs (status, lease_until);

CREATE TABLE harness_outbox_events (
    id VARCHAR(255) NOT NULL,
    run_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    trace_id VARCHAR(255),
    command VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    last_error TEXT,
    CONSTRAINT pk_harness_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_harness_outbox_pending
    ON harness_outbox_events (status, next_attempt_at, created_at);
