-- 模型名称相同但供应商权重或配置发生变化时，使用显式版本隔离旧 embedding 缓存。
ALTER TABLE harness_context_embedding_cache
    ADD COLUMN model_version VARCHAR(128) NOT NULL DEFAULT 'v1';

ALTER TABLE harness_context_embedding_cache
    DROP CONSTRAINT pk_harness_context_embedding_cache;

ALTER TABLE harness_context_embedding_cache
    ADD CONSTRAINT pk_harness_context_embedding_cache
        PRIMARY KEY (tenant_id, content_hash, request_model, model_version, embedding_dimension);

COMMENT ON COLUMN harness_context_embedding_cache.model_version IS '供应商模型或部署配置版本';
