-- 固化批量请求指纹，避免同一 Idempotency-Key 被另一份作业请求静默复用。
ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS batch_request_hash VARCHAR(64);
