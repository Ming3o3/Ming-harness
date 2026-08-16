-- 为编程学习资料和教育 Run 保存可回放的语言上下文。
-- 为空表示通用课程资料，保持历史教育资料和 Run 的检索语义不变。
ALTER TABLE harness_education_sources
    ADD COLUMN IF NOT EXISTS programming_language VARCHAR(64);

ALTER TABLE harness_runs
    ADD COLUMN IF NOT EXISTS education_programming_language VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_harness_education_sources_language
    ON harness_education_sources (tenant_id, programming_language, updated_at DESC);
