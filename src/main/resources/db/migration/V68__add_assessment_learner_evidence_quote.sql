-- 模型形成性评价需保存可核对的学习者原话锚点；人工复核历史记录允许为空。
ALTER TABLE harness_assessment_attempts
    ADD COLUMN learner_evidence_quote VARCHAR(2000);
