-- 冻结 CALIBRATED 教育 Run 创建时使用的教师标注权重，保证历史检索可回放。
ALTER TABLE harness_runs ADD COLUMN education_retrieval_weights TEXT;
