-- 修复早期教育 Run 把详细学习目标标题写入知识点字段的历史数据。
-- 仅修复 Run 与同租户、同用户学习目标绑定且字段完全等于目标标题的记录，
-- 不对任意相似文本做模糊替换，避免破坏真实的知识点标签。
UPDATE harness_runs AS r
SET education_concept_key = g.concept_key
FROM harness_learning_goals AS g
WHERE r.education_learning_goal_id = g.id
  AND r.tenant_id = g.tenant_id
  AND r.user_id = g.user_id
  AND r.education_concept_key IS NOT NULL
  AND r.education_learning_goal_title IS NOT NULL
  AND lower(trim(r.education_concept_key)) = lower(trim(r.education_learning_goal_title))
  AND lower(trim(r.education_concept_key)) <> lower(trim(g.concept_key));
