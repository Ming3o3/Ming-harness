-- 将教师布置作业时选择的编程语言冻结到作业实例，确保学生从作业入口启动 Run 时
-- 始终沿用同一语言上下文；为空表示历史作业或通用编程作业。
ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS programming_language VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_harness_learning_assignments_language
    ON harness_learning_assignments (tenant_id, programming_language, updated_at DESC);
