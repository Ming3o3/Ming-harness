ALTER TABLE harness_assessment_attempts
    ADD COLUMN evidence_source VARCHAR(32) NOT NULL DEFAULT 'MODEL_TOOL';

ALTER TABLE harness_assessment_attempts
    ADD COLUMN evidence_text VARCHAR(4000);
