-- 形成性测评必须绑定学习目标、Run 和具体工具步骤，保留掌握度变化前后快照。
CREATE TABLE harness_assessment_attempts (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    run_id VARCHAR(255) NOT NULL,
    step_id VARCHAR(255) NOT NULL,
    learning_goal_id VARCHAR(255) NOT NULL,
    learner_profile_id VARCHAR(255) NOT NULL,
    concept_key VARCHAR(255) NOT NULL,
    correct BOOLEAN NOT NULL,
    observed_mastery DOUBLE PRECISION NOT NULL,
    mastery_before DOUBLE PRECISION NOT NULL,
    mastery_after DOUBLE PRECISION NOT NULL,
    feedback VARCHAR(1000),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_assessment_attempts PRIMARY KEY (id),
    CONSTRAINT ck_harness_assessment_observed CHECK (observed_mastery BETWEEN 0 AND 1),
    CONSTRAINT ck_harness_assessment_before CHECK (mastery_before BETWEEN 0 AND 1),
    CONSTRAINT ck_harness_assessment_after CHECK (mastery_after BETWEEN 0 AND 1)
);

CREATE INDEX idx_harness_assessment_goal
    ON harness_assessment_attempts (tenant_id, user_id, learning_goal_id, created_at ASC);
CREATE INDEX idx_harness_assessment_run
    ON harness_assessment_attempts (tenant_id, user_id, run_id, created_at ASC);
