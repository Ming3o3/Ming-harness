-- 将测评事实与该 Run 实际使用的课程知识源建立可审计关联；只保存来源引用，不复制正文。
ALTER TABLE harness_assessment_attempts
    ADD COLUMN IF NOT EXISTS retrieval_evidence_json TEXT NOT NULL DEFAULT '[]';
