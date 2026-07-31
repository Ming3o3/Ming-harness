-- 为数据保留清理任务增加时间索引，避免全表扫描影响线上请求。
CREATE INDEX IF NOT EXISTS idx_harness_runs_terminal_finished
    ON harness_runs (status, finished_at);
CREATE INDEX IF NOT EXISTS idx_harness_memories_expired
    ON harness_context_memories (expires_at, deleted_at);
CREATE INDEX IF NOT EXISTS idx_harness_documents_deleted
    ON harness_context_documents (deleted_at);
CREATE INDEX IF NOT EXISTS idx_harness_evaluation_created
    ON harness_evaluation_reports (created_at);
CREATE INDEX IF NOT EXISTS idx_harness_outbox_status_created
    ON harness_outbox_events (status, created_at);
