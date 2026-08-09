-- 结构化学习目标，作为教育 Run、测评记录和后续推荐的业务主键。
CREATE TABLE harness_learning_goals (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    learner_profile_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    concept_key VARCHAR(255) NOT NULL,
    baseline_mastery DOUBLE PRECISION NOT NULL,
    target_mastery DOUBLE PRECISION NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_learning_goals PRIMARY KEY (id),
    CONSTRAINT ck_harness_learning_goal_baseline CHECK (baseline_mastery BETWEEN 0 AND 1),
    CONSTRAINT ck_harness_learning_goal_target CHECK (target_mastery BETWEEN 0 AND 1)
);

CREATE INDEX idx_harness_learning_goals_user
    ON harness_learning_goals (tenant_id, user_id, status, updated_at DESC);
CREATE INDEX idx_harness_learning_goals_profile
    ON harness_learning_goals (tenant_id, learner_profile_id, status, updated_at DESC);

ALTER TABLE harness_runs ADD COLUMN education_learning_goal_id VARCHAR(128);
ALTER TABLE harness_runs ADD COLUMN education_learning_goal_title VARCHAR(255);
ALTER TABLE harness_runs ADD COLUMN education_learning_goal_baseline DOUBLE PRECISION;
ALTER TABLE harness_runs ADD COLUMN education_learning_goal_target DOUBLE PRECISION;

CREATE INDEX idx_harness_runs_learning_goal
    ON harness_runs (tenant_id, education_learning_goal_id, created_at DESC);
