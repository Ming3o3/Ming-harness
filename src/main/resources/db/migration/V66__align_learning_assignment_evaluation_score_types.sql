-- LearningAssignmentEvaluation uses Java int fields, which Hibernate maps to INTEGER.
-- V65 created these columns as SMALLINT, causing schema validation to fail on PostgreSQL.
-- Widen the existing score columns so Flyway-managed databases match the JPA mapping.
ALTER TABLE harness_learning_assignment_evaluations
    ALTER COLUMN content_correctness_score TYPE INTEGER
        USING content_correctness_score::INTEGER,
    ALTER COLUMN evidence_quality_score TYPE INTEGER
        USING evidence_quality_score::INTEGER,
    ALTER COLUMN transfer_readiness_score TYPE INTEGER
        USING transfer_readiness_score::INTEGER;
