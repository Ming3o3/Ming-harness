-- 多实例 Outbox Relay 使用短期发布租约，避免同时发布同一条待处理事件。
ALTER TABLE harness_outbox_events ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(255);
ALTER TABLE harness_outbox_events ADD COLUMN IF NOT EXISTS claim_until TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_harness_outbox_claim
    ON harness_outbox_events (status, claim_until, next_attempt_at, created_at);
