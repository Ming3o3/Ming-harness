-- 教育知识库 Agent 的课程约束元数据与学习者状态。
-- 正文仍存于 harness_context_documents，避免教育模式复制敏感知识正文。

CREATE TABLE harness_education_sources (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    grade_level VARCHAR(128) NOT NULL,
    curriculum_version VARCHAR(128) NOT NULL,
    chapter VARCHAR(255),
    learning_objectives TEXT,
    concept_tags TEXT,
    prerequisite_concepts TEXT,
    difficulty_level INTEGER NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_education_sources PRIMARY KEY (id),
    CONSTRAINT uk_harness_education_source_document UNIQUE (tenant_id, document_id),
    CONSTRAINT ck_harness_education_source_difficulty CHECK (difficulty_level BETWEEN 1 AND 5)
);

CREATE TABLE harness_learner_profiles (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    grade_level VARCHAR(128) NOT NULL,
    curriculum_version VARCHAR(128) NOT NULL,
    learning_goal VARCHAR(512),
    language VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learner_profiles PRIMARY KEY (id),
    CONSTRAINT uk_harness_learner_profile_scope UNIQUE
        (tenant_id, user_id, subject, grade_level, curriculum_version)
);

CREATE TABLE harness_learner_mastery (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    learner_profile_id VARCHAR(255) NOT NULL,
    concept_key VARCHAR(255) NOT NULL,
    mastery_score DOUBLE PRECISION NOT NULL,
    attempts INTEGER NOT NULL,
    correct_attempts INTEGER NOT NULL,
    last_assessed_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_learner_mastery PRIMARY KEY (id),
    CONSTRAINT uk_harness_learner_mastery_concept UNIQUE (learner_profile_id, concept_key),
    CONSTRAINT ck_harness_learner_mastery_score CHECK (mastery_score BETWEEN 0 AND 1),
    CONSTRAINT ck_harness_learner_mastery_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_harness_learner_mastery_correct CHECK (correct_attempts >= 0 AND correct_attempts <= attempts)
);

CREATE INDEX idx_harness_education_sources_filter
    ON harness_education_sources (tenant_id, subject, grade_level, curriculum_version, deleted_at);
CREATE INDEX idx_harness_learner_profiles_user
    ON harness_learner_profiles (tenant_id, user_id, active, updated_at DESC);
CREATE INDEX idx_harness_learner_mastery_profile
    ON harness_learner_mastery (tenant_id, learner_profile_id, concept_key);
