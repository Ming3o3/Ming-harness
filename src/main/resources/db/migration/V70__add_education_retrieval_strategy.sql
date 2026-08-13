-- 冻结教育 Run 使用的检索策略，支持基线与消融实验的可复现回放。
ALTER TABLE harness_runs
    ADD COLUMN education_retrieval_strategy VARCHAR(32);

UPDATE harness_runs
   SET education_retrieval_strategy = 'FULL'
 WHERE education_mode = TRUE
   AND (education_retrieval_strategy IS NULL OR education_retrieval_strategy = '');

CREATE INDEX idx_harness_runs_education_retrieval_strategy
    ON harness_runs (tenant_id, education_mode, education_retrieval_strategy, created_at DESC);
