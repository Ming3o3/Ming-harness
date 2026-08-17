-- 让编程语言专属知识依赖边与通用课程依赖边可以共存。
ALTER TABLE harness_education_concept_dependencies
    ADD COLUMN IF NOT EXISTS programming_language VARCHAR(64);

ALTER TABLE harness_education_concept_dependencies
    DROP CONSTRAINT IF EXISTS uk_harness_education_dependency_source_edge;

ALTER TABLE harness_education_concept_dependencies
    ADD CONSTRAINT uk_harness_education_dependency_source_edge
        UNIQUE (tenant_id, source_document_id, programming_language,
                concept_key, prerequisite_concept);

CREATE INDEX IF NOT EXISTS idx_harness_education_dependency_language_scope
    ON harness_education_concept_dependencies
       (tenant_id, subject, grade_level, curriculum_version,
        programming_language, concept_key);
