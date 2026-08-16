-- 保存 BKT 所需的题目难度和结构化知识点证据。
ALTER TABLE harness_assessment_attempts
    ADD COLUMN difficulty_level INTEGER NOT NULL DEFAULT 3;

ALTER TABLE harness_assessment_attempts
    ADD COLUMN knowledge_point_scores_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE harness_assessment_attempts
    ADD COLUMN hint_used BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE harness_assessment_attempts
    ADD COLUMN independent_evidence BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE harness_assessment_attempts
    ADD COLUMN question_type VARCHAR(64);

ALTER TABLE harness_assessment_attempts
    ADD CONSTRAINT ck_harness_assessment_difficulty CHECK (difficulty_level BETWEEN 1 AND 5);

CREATE INDEX idx_harness_assessment_concept_created
    ON harness_assessment_attempts (tenant_id, user_id, concept_key, created_at ASC);
