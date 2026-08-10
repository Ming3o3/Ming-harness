-- 批量布置以教师提供的幂等键作为持久化批次标识；同一课程、批次和学习者只能生成一份作业。
ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS batch_id VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_harness_learning_assignment_course_batch_learner
    ON harness_learning_assignments (tenant_id, course_id, batch_id, learner_user_id);
CREATE INDEX IF NOT EXISTS idx_harness_learning_assignments_course_batch
    ON harness_learning_assignments (tenant_id, course_id, batch_id, created_at);
