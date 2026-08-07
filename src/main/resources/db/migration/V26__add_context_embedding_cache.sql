-- 按租户、正文哈希、请求模型和向量维度缓存 embedding，减少重建索引时的重复供应商调用。
-- 缓存不与具体 chunk 建立外键：同一正文被重新分块后仍可安全复用，租户字段保证隔离。
CREATE TABLE harness_context_embedding_cache (
    tenant_id VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    request_model VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    embedding vector(1536) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_context_embedding_cache
        PRIMARY KEY (tenant_id, content_hash, request_model, embedding_dimension),
    CONSTRAINT ck_harness_context_embedding_cache_dimension CHECK (embedding_dimension > 0)
);

CREATE INDEX idx_harness_context_embedding_cache_updated
    ON harness_context_embedding_cache (updated_at);

COMMENT ON TABLE harness_context_embedding_cache IS '按租户和 embedding 模型隔离的可复用向量缓存';
