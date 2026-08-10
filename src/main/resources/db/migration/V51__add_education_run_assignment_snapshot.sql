-- 固化教育 Run 与课程作业的关联，避免只凭学习目标反推业务归属。
ALTER TABLE harness_runs
    ADD COLUMN education_learning_assignment_id VARCHAR(128);

CREATE INDEX idx_harness_runs_education_assignment
    ON harness_runs (tenant_id, education_learning_assignment_id, created_at DESC);
