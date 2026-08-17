-- 将不同编程语言的代码评测诊断归一为可统计类别。
-- 类别用于形成性证据分析和教学决策，不等同于教师评分或知识掌握结论。
ALTER TABLE harness_learning_assignment_submissions
    ADD COLUMN IF NOT EXISTS code_diagnostic_category VARCHAR(32) NOT NULL DEFAULT 'NONE';

CREATE INDEX IF NOT EXISTS idx_harness_assignment_submissions_diagnostic
    ON harness_learning_assignment_submissions (tenant_id, code_diagnostic_category,
                                                  submitted_at DESC);
