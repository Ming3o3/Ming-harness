CREATE TABLE IF NOT EXISTS harness_learning_assignment_submissions (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    learning_assignment_id VARCHAR(255) NOT NULL,
    learner_user_id VARCHAR(255) NOT NULL,
    run_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_harness_learning_assignment_submission_run
        UNIQUE (tenant_id, learning_assignment_id, run_id)
);

CREATE INDEX IF NOT EXISTS idx_harness_learning_assignment_submission_assignment
    ON harness_learning_assignment_submissions (tenant_id, learning_assignment_id, submitted_at DESC);
