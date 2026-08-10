-- 将形成性测评证据直接绑定到课程作业，避免同一学习目标被多个业务入口复用时串证据。
ALTER TABLE harness_assessment_attempts
    ADD COLUMN learning_assignment_id VARCHAR(128);

CREATE INDEX idx_harness_assessment_attempts_assignment
    ON harness_assessment_attempts (tenant_id, user_id, learning_assignment_id, created_at);
