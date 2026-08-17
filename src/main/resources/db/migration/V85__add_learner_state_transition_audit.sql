-- 保存每次学习者掌握度变化的前后状态、证据来源和 Run 关联，供回放与实验导出。
CREATE TABLE harness_learner_state_transitions (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    learner_user_id VARCHAR(255) NOT NULL,
    learner_profile_id VARCHAR(255) NOT NULL,
    concept_key VARCHAR(255) NOT NULL,
    run_id VARCHAR(255),
    before_mastery DOUBLE PRECISION NOT NULL,
    after_mastery DOUBLE PRECISION NOT NULL,
    before_effective_mastery DOUBLE PRECISION NOT NULL,
    after_effective_mastery DOUBLE PRECISION NOT NULL,
    before_retention_score DOUBLE PRECISION NOT NULL,
    after_retention_score DOUBLE PRECISION NOT NULL,
    before_attempts INTEGER NOT NULL,
    after_attempts INTEGER NOT NULL,
    before_correct_attempts INTEGER NOT NULL,
    after_correct_attempts INTEGER NOT NULL,
    evidence_source VARCHAR(64) NOT NULL,
    assessment_type VARCHAR(32) NOT NULL,
    diagnostic_category VARCHAR(64),
    evidence_text VARCHAR(4000),
    behavior_test_pass_rate DOUBLE PRECISION,
    difficulty_level INTEGER NOT NULL,
    evidence_weight DOUBLE PRECISION NOT NULL,
    hint_used BOOLEAN NOT NULL,
    independent_evidence BOOLEAN NOT NULL,
    state_model_version VARCHAR(64) NOT NULL,
    retention_model_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learner_state_transitions PRIMARY KEY (id),
    CONSTRAINT ck_harness_state_transition_before_mastery
        CHECK (before_mastery >= 0 AND before_mastery <= 1),
    CONSTRAINT ck_harness_state_transition_after_mastery
        CHECK (after_mastery >= 0 AND after_mastery <= 1),
    CONSTRAINT ck_harness_state_transition_before_effective
        CHECK (before_effective_mastery >= 0 AND before_effective_mastery <= 1),
    CONSTRAINT ck_harness_state_transition_after_effective
        CHECK (after_effective_mastery >= 0 AND after_effective_mastery <= 1),
    CONSTRAINT ck_harness_state_transition_before_retention
        CHECK (before_retention_score >= 0 AND before_retention_score <= 1),
    CONSTRAINT ck_harness_state_transition_after_retention
        CHECK (after_retention_score >= 0 AND after_retention_score <= 1),
    CONSTRAINT ck_harness_state_transition_difficulty
        CHECK (difficulty_level BETWEEN 1 AND 5),
    CONSTRAINT ck_harness_state_transition_weight
        CHECK (evidence_weight >= 0 AND evidence_weight <= 1),
    CONSTRAINT ck_harness_state_transition_pass_rate
        CHECK (behavior_test_pass_rate IS NULL OR
               (behavior_test_pass_rate >= 0 AND behavior_test_pass_rate <= 1))
);

CREATE INDEX idx_harness_state_transition_profile_created
    ON harness_learner_state_transitions (tenant_id, learner_profile_id, created_at DESC);

CREATE INDEX idx_harness_state_transition_concept_created
    ON harness_learner_state_transitions (tenant_id, learner_profile_id, concept_key, created_at DESC);

CREATE INDEX idx_harness_state_transition_run_created
    ON harness_learner_state_transitions (tenant_id, run_id, created_at ASC);
