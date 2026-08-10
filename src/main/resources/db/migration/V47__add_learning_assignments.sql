-- 将教师/组织布置的课程作业接入学习画像和学习目标，形成业务入口。
CREATE TABLE harness_learning_assignments (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    teacher_user_id VARCHAR(255) NOT NULL,
    learner_user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    instructions TEXT NOT NULL,
    subject VARCHAR(128) NOT NULL,
    grade_level VARCHAR(128) NOT NULL,
    curriculum_version VARCHAR(128) NOT NULL,
    concept_key VARCHAR(255) NOT NULL,
    target_mastery DOUBLE PRECISION NOT NULL,
    due_at TIMESTAMP(6) WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    learner_profile_id VARCHAR(255),
    learning_goal_id VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_learning_assignments PRIMARY KEY (id),
    CONSTRAINT ck_harness_learning_assignment_target CHECK (
        target_mastery >= 0.01 AND target_mastery <= 1.0
    )
);

CREATE INDEX idx_harness_learning_assignments_teacher
    ON harness_learning_assignments (tenant_id, teacher_user_id, created_at);
CREATE INDEX idx_harness_learning_assignments_learner
    ON harness_learning_assignments (tenant_id, learner_user_id, status, due_at);
CREATE INDEX idx_harness_learning_assignments_goal
    ON harness_learning_assignments (tenant_id, learning_goal_id);
