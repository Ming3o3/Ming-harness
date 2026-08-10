-- 将学习者达标事实与教师业务确认分离，避免自动达标被直接当作交付结果。
ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS teacher_reviewed_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS teacher_reviewer_user_id VARCHAR(255);
ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS teacher_review_note TEXT;
