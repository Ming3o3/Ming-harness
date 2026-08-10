-- 课程结课时固化一份不可被后续保持度复习改写的结果快照。
CREATE TABLE harness_education_course_results (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    course_id VARCHAR(255) NOT NULL,
    active_learner_total BIGINT NOT NULL,
    learners_with_assignments BIGINT NOT NULL,
    effective_assignment_total BIGINT NOT NULL,
    assignment_completed BIGINT NOT NULL,
    assignment_verified BIGINT NOT NULL,
    submission_covered BIGINT NOT NULL,
    average_mastery_progress DOUBLE PRECISION NOT NULL,
    average_mastery_gain DOUBLE PRECISION NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_by_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_education_course_results PRIMARY KEY (id),
    CONSTRAINT uk_harness_education_course_result_course UNIQUE (tenant_id, course_id),
    CONSTRAINT ck_harness_education_course_result_counts CHECK (
        active_learner_total >= 0
        AND learners_with_assignments >= 0
        AND effective_assignment_total >= 0
        AND assignment_completed >= 0
        AND assignment_verified >= 0
        AND submission_covered >= 0
    )
);

CREATE INDEX idx_harness_education_course_result_tenant_time
    ON harness_education_course_results (tenant_id, completed_at DESC);

CREATE TABLE harness_education_course_learner_results (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    course_result_id VARCHAR(255) NOT NULL,
    course_id VARCHAR(255) NOT NULL,
    learner_user_id VARCHAR(255) NOT NULL,
    effective_assignment_total BIGINT NOT NULL,
    assignment_completed BIGINT NOT NULL,
    assignment_verified BIGINT NOT NULL,
    submission_covered BIGINT NOT NULL,
    average_mastery_progress DOUBLE PRECISION NOT NULL,
    average_mastery_gain DOUBLE PRECISION NOT NULL,
    last_activity_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_education_course_learner_results PRIMARY KEY (id),
    CONSTRAINT uk_harness_education_course_learner_result_member
        UNIQUE (tenant_id, course_result_id, learner_user_id),
    CONSTRAINT fk_harness_education_course_learner_result_course
        FOREIGN KEY (course_result_id) REFERENCES harness_education_course_results (id),
    CONSTRAINT ck_harness_education_course_learner_result_counts CHECK (
        effective_assignment_total >= 0
        AND assignment_completed >= 0
        AND assignment_verified >= 0
        AND submission_covered >= 0
    )
);

CREATE INDEX idx_harness_education_course_learner_result_course
    ON harness_education_course_learner_results (tenant_id, course_id, learner_user_id);
