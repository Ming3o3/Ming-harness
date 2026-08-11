-- 固化直接学习会话绑定的课程实例，保证课程版本约束和后续审计可复现。
ALTER TABLE harness_runs
    ADD COLUMN education_course_id VARCHAR(128);

ALTER TABLE harness_runs
    ADD COLUMN education_course_code VARCHAR(128);

ALTER TABLE harness_runs
    ADD COLUMN education_course_title VARCHAR(255);

CREATE INDEX idx_harness_runs_education_course
    ON harness_runs (tenant_id, education_course_id, created_at DESC);
