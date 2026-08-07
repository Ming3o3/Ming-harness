ALTER TABLE harness_context_chunks
    ADD COLUMN chunk_strategy VARCHAR(32);

ALTER TABLE harness_context_chunks
    ADD COLUMN chunker_version VARCHAR(64);

UPDATE harness_context_chunks
   SET chunk_strategy = 'DETERMINISTIC',
       chunker_version = 'deterministic-v1'
 WHERE chunk_strategy IS NULL OR chunker_version IS NULL;

ALTER TABLE harness_context_chunks
    ALTER COLUMN chunk_strategy SET NOT NULL;

ALTER TABLE harness_context_chunks
    ALTER COLUMN chunker_version SET NOT NULL;

COMMENT ON COLUMN harness_context_chunks.chunk_strategy IS '生成该子块的分块策略';
COMMENT ON COLUMN harness_context_chunks.chunker_version IS '分块算法版本，算法变更后用于重建索引';
