-- 已完成学习目标的保持度复习计划，以及可审计的复习型测评尝试。
CREATE TABLE harness_learning_review_plans (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    learning_goal_id VARCHAR(255) NOT NULL,
    learner_profile_id VARCHAR(255) NOT NULL,
    concept_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    review_count INTEGER NOT NULL DEFAULT 0,
    successful_review_count INTEGER NOT NULL DEFAULT 0,
    interval_days INTEGER NOT NULL DEFAULT 0,
    next_review_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_reviewed_at TIMESTAMP(6) WITH TIME ZONE,
    last_review_correct BOOLEAN,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learning_review_plans PRIMARY KEY (id),
    CONSTRAINT uk_harness_learning_review_goal UNIQUE (tenant_id, user_id, learning_goal_id),
    CONSTRAINT ck_harness_learning_review_count CHECK (review_count >= 0),
    CONSTRAINT ck_harness_learning_review_success CHECK (
        successful_review_count >= 0 AND successful_review_count <= review_count
    ),
    CONSTRAINT ck_harness_learning_review_interval CHECK (interval_days >= 0)
);

CREATE INDEX idx_harness_learning_review_due
    ON harness_learning_review_plans (tenant_id, user_id, status, next_review_at);
CREATE INDEX idx_harness_learning_review_profile
    ON harness_learning_review_plans (tenant_id, learner_profile_id, status, next_review_at);

-- 复习 Run 需要在执行快照中保留计划身份，避免完成目标后重新绑定成普通对话。
ALTER TABLE harness_runs ADD COLUMN education_review_plan_id VARCHAR(255);
CREATE INDEX idx_harness_runs_review_plan
    ON harness_runs (tenant_id, education_review_plan_id, created_at DESC);

-- 同一测评事实流同时承载形成性测评和保持度复习，类型和计划身份可追踪。
ALTER TABLE harness_assessment_attempts
    ADD COLUMN assessment_type VARCHAR(32) NOT NULL DEFAULT 'FORMATIVE';
ALTER TABLE harness_assessment_attempts
    ADD COLUMN review_plan_id VARCHAR(255);
CREATE INDEX idx_harness_assessment_review_plan
    ON harness_assessment_attempts (tenant_id, user_id, review_plan_id, created_at ASC);

-- 为历史上已手工标记完成但尚未有计划的目标补建首个即时复习计划。
INSERT INTO harness_learning_review_plans (
    id, tenant_id, user_id, learning_goal_id, learner_profile_id, concept_key,
    status, review_count, successful_review_count, interval_days, next_review_at,
    last_reviewed_at, last_review_correct, created_at, updated_at
)
SELECT
    'legacy-review-' || id, tenant_id, user_id, id, learner_profile_id, concept_key,
    'ACTIVE', 0, 0, 0,
    COALESCE(completed_at, updated_at), NULL, NULL, created_at, updated_at
FROM harness_learning_goals
WHERE status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1
      FROM harness_learning_review_plans plan
      WHERE plan.tenant_id = harness_learning_goals.tenant_id
        AND plan.user_id = harness_learning_goals.user_id
        AND plan.learning_goal_id = harness_learning_goals.id
  );
