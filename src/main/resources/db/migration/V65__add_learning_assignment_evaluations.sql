CREATE TABLE IF NOT EXISTS harness_learning_assignment_evaluations (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    learning_assignment_id VARCHAR(255) NOT NULL,
    course_id VARCHAR(255),
    learner_user_id VARCHAR(255) NOT NULL,
    evaluator_user_id VARCHAR(255) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    rubric_version VARCHAR(64) NOT NULL,
    content_correctness_score SMALLINT NOT NULL,
    evidence_quality_score SMALLINT NOT NULL,
    transfer_readiness_score SMALLINT NOT NULL,
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_harness_learning_assignment_evaluation_decision
        CHECK (decision IN ('VERIFY', 'RETURN')),
    CONSTRAINT ck_harness_learning_assignment_evaluation_scores
        CHECK (content_correctness_score BETWEEN 1 AND 5
            AND evidence_quality_score BETWEEN 1 AND 5
            AND transfer_readiness_score BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_harness_learning_assignment_evaluation_assignment
    ON harness_learning_assignment_evaluations (tenant_id, learning_assignment_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_harness_learning_assignment_evaluation_participant
    ON harness_learning_assignment_evaluations (tenant_id, learner_user_id, created_at DESC);
