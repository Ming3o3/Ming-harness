-- 将课程作业的布置、接受、逾期、完成和取消状态接入站内触达。
CREATE TABLE harness_learning_assignment_notifications (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    learning_assignment_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    seen_at TIMESTAMP(6) WITH TIME ZONE,
    read_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learning_assignment_notifications PRIMARY KEY (id),
    CONSTRAINT uk_harness_learning_assignment_notification_event UNIQUE (
        tenant_id, user_id, learning_assignment_id, event_key
    )
);

CREATE INDEX idx_harness_learning_assignment_notifications_user_status
    ON harness_learning_assignment_notifications (tenant_id, user_id, status, created_at);
CREATE INDEX idx_harness_learning_assignment_notifications_assignment
    ON harness_learning_assignment_notifications (tenant_id, learning_assignment_id, created_at);
