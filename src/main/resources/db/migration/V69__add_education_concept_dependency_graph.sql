-- 将课程资料中的前置知识标签物化为可查询的知识依赖图边。
CREATE TABLE harness_education_concept_dependencies (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    grade_level VARCHAR(128) NOT NULL,
    curriculum_version VARCHAR(128) NOT NULL,
    concept_key VARCHAR(255) NOT NULL,
    prerequisite_concept VARCHAR(255) NOT NULL,
    source_document_id VARCHAR(255) NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_education_concept_dependencies PRIMARY KEY (id),
    CONSTRAINT uk_harness_education_dependency_source_edge
        UNIQUE (tenant_id, source_document_id, concept_key, prerequisite_concept),
    CONSTRAINT ck_harness_education_dependency_confidence
        CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX idx_harness_education_dependency_scope
    ON harness_education_concept_dependencies
       (tenant_id, subject, grade_level, curriculum_version, concept_key);

CREATE INDEX idx_harness_education_dependency_prerequisite
    ON harness_education_concept_dependencies
       (tenant_id, subject, grade_level, curriculum_version, prerequisite_concept);

CREATE INDEX idx_harness_education_dependency_source
    ON harness_education_concept_dependencies (tenant_id, source_document_id);
