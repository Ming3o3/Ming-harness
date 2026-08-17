-- 编程行为测试的形成性证据摘要；不替代教师量规确认。
ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_behavior_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED';

ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_test_case_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_passed_test_case_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_test_pass_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0;

CREATE INDEX IF NOT EXISTS idx_harness_assignment_submissions_behavior
    ON harness_learning_assignment_submissions (tenant_id, code_behavior_status, submitted_at DESC);
