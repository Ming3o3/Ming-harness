-- 教师为编程作业配置的可审计行为测试用例；Run 创建时会冻结启用用例的快照。
CREATE TABLE IF NOT EXISTS harness_learning_assignment_test_cases (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    learning_assignment_id VARCHAR(255) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    name VARCHAR(255),
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sequence INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_harness_assignment_test_case_key
        UNIQUE (tenant_id, learning_assignment_id, case_key)
);

CREATE INDEX IF NOT EXISTS idx_harness_assignment_test_cases_order
    ON harness_learning_assignment_test_cases (tenant_id, learning_assignment_id, enabled, sequence, created_at);

ALTER TABLE harness_runs
    ADD COLUMN IF NOT EXISTS education_programming_test_cases TEXT;
