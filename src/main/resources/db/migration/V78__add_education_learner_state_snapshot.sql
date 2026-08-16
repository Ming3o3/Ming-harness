-- 冻结掌握度、观测次数和答对次数，供不确定性感知检索回放。
ALTER TABLE harness_runs
    ADD COLUMN education_learner_state_snapshot TEXT;
