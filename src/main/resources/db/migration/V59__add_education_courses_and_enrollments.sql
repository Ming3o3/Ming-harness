-- 将单人作业提升为可运营的课程实例与名单：保留历史作业事实，课程归档后禁止新增名单和作业。
CREATE TABLE IF NOT EXISTS harness_education_courses (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    owner_user_id VARCHAR(255) NOT NULL,
    code VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    grade_level VARCHAR(128) NOT NULL,
    curriculum_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_education_courses PRIMARY KEY (id),
    CONSTRAINT uk_harness_education_course_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_harness_education_courses_owner
    ON harness_education_courses (tenant_id, owner_user_id, updated_at);

CREATE TABLE IF NOT EXISTS harness_education_enrollments (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    course_id VARCHAR(255) NOT NULL,
    learner_user_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    enrolled_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    removed_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_education_enrollments PRIMARY KEY (id),
    CONSTRAINT uk_harness_education_enrollment_member UNIQUE (tenant_id, course_id, learner_user_id)
);

CREATE INDEX IF NOT EXISTS idx_harness_education_enrollments_course
    ON harness_education_enrollments (tenant_id, course_id, status, enrolled_at);
CREATE INDEX IF NOT EXISTS idx_harness_education_enrollments_learner
    ON harness_education_enrollments (tenant_id, learner_user_id, status);

ALTER TABLE harness_learning_assignments
    ADD COLUMN IF NOT EXISTS course_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_harness_learning_assignments_course
    ON harness_learning_assignments (tenant_id, course_id, teacher_user_id, created_at);
