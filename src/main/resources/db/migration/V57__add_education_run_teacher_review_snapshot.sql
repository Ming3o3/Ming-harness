-- 将教师退回返工说明冻结到教育 Run，保证下一轮 Agent 使用与通知相同的业务约束。
ALTER TABLE harness_runs
    ADD COLUMN IF NOT EXISTS education_learning_assignment_teacher_review_note TEXT;
