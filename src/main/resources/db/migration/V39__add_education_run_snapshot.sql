-- 冻结教育 Agent Run 的课程约束和学习者掌握度摘要，保证异步执行与实验可复现。

ALTER TABLE harness_runs
    ADD COLUMN education_mode BOOLEAN;
ALTER TABLE harness_runs
    ADD COLUMN education_learner_profile_id VARCHAR(128);
ALTER TABLE harness_runs
    ADD COLUMN education_subject VARCHAR(128);
ALTER TABLE harness_runs
    ADD COLUMN education_grade_level VARCHAR(128);
ALTER TABLE harness_runs
    ADD COLUMN education_curriculum_version VARCHAR(128);
ALTER TABLE harness_runs
    ADD COLUMN education_concept_key VARCHAR(255);
ALTER TABLE harness_runs
    ADD COLUMN education_min_difficulty INTEGER;
ALTER TABLE harness_runs
    ADD COLUMN education_max_difficulty INTEGER;
ALTER TABLE harness_runs
    ADD COLUMN education_pedagogical_mode VARCHAR(64);
ALTER TABLE harness_runs
    ADD COLUMN education_learner_state TEXT;

CREATE INDEX idx_harness_runs_education_scope
    ON harness_runs (tenant_id, education_mode, education_subject, education_grade_level,
                     education_curriculum_version, created_at DESC);
