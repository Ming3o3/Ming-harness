-- 将复习计划实例化为可触达、可执行、可回写结果的学习业务任务。
CREATE TABLE harness_learning_tasks (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    learning_goal_id VARCHAR(255) NOT NULL,
    review_plan_id VARCHAR(255) NOT NULL,
    review_sequence INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    scheduled_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    conversation_id VARCHAR(255),
    run_id VARCHAR(255),
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    outcome_correct BOOLEAN,
    defer_count INTEGER NOT NULL DEFAULT 0,
    last_deferred_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learning_tasks PRIMARY KEY (id),
    CONSTRAINT uk_harness_learning_task_review_occurrence UNIQUE (
        tenant_id, user_id, review_plan_id, review_sequence
    ),
    CONSTRAINT ck_harness_learning_task_sequence CHECK (review_sequence >= 0),
    CONSTRAINT ck_harness_learning_task_defer_count CHECK (defer_count >= 0)
);

CREATE INDEX idx_harness_learning_tasks_user_status
    ON harness_learning_tasks (tenant_id, user_id, status, scheduled_at);
CREATE INDEX idx_harness_learning_tasks_goal
    ON harness_learning_tasks (tenant_id, user_id, learning_goal_id, scheduled_at);
CREATE INDEX idx_harness_learning_tasks_run
    ON harness_learning_tasks (tenant_id, user_id, run_id);
