-- 冻结教育 Run 创建时的知识依赖图，保证重试和实验回放不受教师后续改图影响。
ALTER TABLE harness_runs
    ADD COLUMN education_dependency_graph TEXT;
