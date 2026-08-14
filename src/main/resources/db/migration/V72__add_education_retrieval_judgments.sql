-- 保存教师或独立评价者对教育检索证据的结构化判断，为排序校准提供可回放事实。
CREATE TABLE harness_education_retrieval_judgments (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    run_id VARCHAR(255) NOT NULL,
    step_id VARCHAR(255),
    evaluator_user_id VARCHAR(255) NOT NULL,
    document_id VARCHAR(255),
    evidence_citation VARCHAR(1500) NOT NULL,
    target_grounding_score INTEGER NOT NULL,
    prerequisite_utility_score INTEGER NOT NULL,
    difficulty_fit_score INTEGER NOT NULL,
    overall_utility_score INTEGER NOT NULL,
    rubric_version VARCHAR(64) NOT NULL,
    note TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_education_retrieval_judgments PRIMARY KEY (id),
    CONSTRAINT ck_harness_retrieval_judgment_target_score
        CHECK (target_grounding_score BETWEEN 1 AND 5),
    CONSTRAINT ck_harness_retrieval_judgment_prerequisite_score
        CHECK (prerequisite_utility_score BETWEEN 1 AND 5),
    CONSTRAINT ck_harness_retrieval_judgment_difficulty_score
        CHECK (difficulty_fit_score BETWEEN 1 AND 5),
    CONSTRAINT ck_harness_retrieval_judgment_overall_score
        CHECK (overall_utility_score BETWEEN 1 AND 5)
);

CREATE INDEX idx_harness_retrieval_judgment_run
    ON harness_education_retrieval_judgments (tenant_id, run_id, created_at ASC);

CREATE INDEX idx_harness_retrieval_judgment_calibration
    ON harness_education_retrieval_judgments (tenant_id, rubric_version, created_at ASC);
