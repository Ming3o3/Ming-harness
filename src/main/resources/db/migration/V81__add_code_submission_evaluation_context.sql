-- 将作业提交物扩展为文本/代码两类，并保存代码评测诊断。
-- 评测状态只是学习证据，不自动替代教师确认和量规评价。
ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS submission_type VARCHAR(16) NOT NULL DEFAULT 'TEXT';

ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS programming_language VARCHAR(64);

ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_evaluation_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED';

ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_diagnostics TEXT;

ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_evaluation_duration_ms BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_harness_assignment_submissions_evaluation
    ON harness_learning_assignment_submissions (tenant_id, submission_type, code_evaluation_status,
                                                  submitted_at DESC);
