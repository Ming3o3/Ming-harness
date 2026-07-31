-- 为每个 Run 增加带外部密钥 HMAC 的审计链头和事件封签。
ALTER TABLE harness_runs
    ADD COLUMN IF NOT EXISTS audit_event_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE harness_runs
    ADD COLUMN IF NOT EXISTS audit_head_hash VARCHAR(64);
ALTER TABLE harness_runs
    ADD COLUMN IF NOT EXISTS audit_head_signature VARCHAR(64);

ALTER TABLE harness_audit_events
    ADD COLUMN IF NOT EXISTS integrity_sequence BIGINT;
ALTER TABLE harness_audit_events
    ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64);
ALTER TABLE harness_audit_events
    ADD COLUMN IF NOT EXISTS integrity_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_harness_audit_run_sequence
    ON harness_audit_events (run_id, integrity_sequence);
CREATE INDEX IF NOT EXISTS idx_harness_audit_run_integrity_sequence
    ON harness_audit_events (run_id, integrity_sequence);
