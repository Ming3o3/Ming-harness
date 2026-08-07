-- 有界父窗口：子块用于向量召回，父窗口用于向模型提供连续上下文。
CREATE TABLE harness_context_parent_windows (
    id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    parent_type VARCHAR(32) NOT NULL,
    parent_id VARCHAR(255) NOT NULL,
    window_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    character_count INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_harness_context_parent_windows PRIMARY KEY (id),
    CONSTRAINT uk_harness_context_parent_windows_parent_index
        UNIQUE (parent_type, parent_id, window_index),
    CONSTRAINT ck_harness_context_parent_windows_index CHECK (window_index >= 0),
    CONSTRAINT ck_harness_context_parent_windows_count CHECK (character_count >= 0)
);

CREATE INDEX idx_harness_context_parent_windows_parent
    ON harness_context_parent_windows (parent_type, parent_id, window_index);
CREATE INDEX idx_harness_context_parent_windows_tenant_active
    ON harness_context_parent_windows (tenant_id, deleted_at);

ALTER TABLE harness_context_chunks
    ADD COLUMN parent_window_id VARCHAR(255);

ALTER TABLE harness_context_chunks
    ADD CONSTRAINT fk_harness_context_chunks_parent_window
    FOREIGN KEY (parent_window_id) REFERENCES harness_context_parent_windows(id);

CREATE INDEX idx_harness_context_chunks_parent_window
    ON harness_context_chunks (parent_window_id, chunk_index);

COMMENT ON TABLE harness_context_parent_windows IS '由连续子块物化的有界推理上下文窗口';
COMMENT ON COLUMN harness_context_chunks.parent_window_id IS '该召回子块所属的有界父窗口，可为空以兼容历史索引';
