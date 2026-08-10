-- 将需要用户行动的学习任务状态持久化为站内通知，补齐任务生成后的可触达和确认环节。
CREATE TABLE harness_learning_task_notifications (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    learning_task_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    seen_at TIMESTAMP(6) WITH TIME ZONE,
    read_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learning_task_notifications PRIMARY KEY (id),
    CONSTRAINT uk_harness_learning_task_notification_event UNIQUE (
        tenant_id, user_id, learning_task_id, event_key
    )
);

CREATE INDEX idx_harness_learning_task_notifications_user_status
    ON harness_learning_task_notifications (tenant_id, user_id, status, created_at);
CREATE INDEX idx_harness_learning_task_notifications_task
    ON harness_learning_task_notifications (tenant_id, user_id, learning_task_id, created_at);
