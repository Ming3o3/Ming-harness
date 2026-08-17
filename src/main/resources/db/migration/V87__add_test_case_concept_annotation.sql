-- 允许教师把行为测试用例标注到具体知识点；为空时回退到作业目标知识点。
ALTER TABLE harness_learning_assignment_test_cases
    ADD COLUMN IF NOT EXISTS concept_key VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_harness_assignment_test_case_concept
    ON harness_learning_assignment_test_cases
       (tenant_id, learning_assignment_id, concept_key, enabled, sequence);
