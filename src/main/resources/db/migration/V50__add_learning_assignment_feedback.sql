-- 将教师反馈、重新安排和学习者确认纳入课程作业业务闭环。
CREATE TABLE harness_learning_assignment_feedback (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    learning_assignment_id VARCHAR(255) NOT NULL,
    teacher_user_id VARCHAR(255) NOT NULL,
    learner_user_id VARCHAR(255) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    suggested_due_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learning_assignment_feedback PRIMARY KEY (id)
);

CREATE INDEX idx_harness_learning_assignment_feedback_assignment
    ON harness_learning_assignment_feedback (tenant_id, learning_assignment_id, created_at);
CREATE INDEX idx_harness_learning_assignment_feedback_learner_status
    ON harness_learning_assignment_feedback (tenant_id, learner_user_id, status, created_at);
