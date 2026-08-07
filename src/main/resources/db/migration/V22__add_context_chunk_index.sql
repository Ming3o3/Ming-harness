-- 父文档子块索引基础。embedding 采用 text-embedding-3-small 兼容的 1536 维；
-- 更换维度时必须通过新的迁移重建列和向量索引，不能在运行时混用不同维度。
CREATE TABLE harness_context_chunks (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    parent_type VARCHAR(32) NOT NULL,
    parent_id VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    character_count INTEGER NOT NULL,
    embedding vector(1536),
    embedding_model VARCHAR(128),
    embedded_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_context_chunks PRIMARY KEY (id),
    CONSTRAINT uk_harness_context_chunks_parent_index UNIQUE (parent_type, parent_id, chunk_index),
    CONSTRAINT ck_harness_context_chunks_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_harness_context_chunks_count CHECK (character_count >= 0)
);

CREATE INDEX idx_harness_context_chunks_parent
    ON harness_context_chunks (parent_type, parent_id, chunk_index);
CREATE INDEX idx_harness_context_chunks_tenant_active
    ON harness_context_chunks (tenant_id, deleted_at);
CREATE INDEX idx_harness_context_chunks_embedding_hnsw
    ON harness_context_chunks USING hnsw (embedding vector_cosine_ops)
    WHERE deleted_at IS NULL AND embedding IS NOT NULL;
