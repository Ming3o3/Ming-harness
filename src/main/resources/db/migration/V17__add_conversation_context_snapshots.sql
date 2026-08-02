-- 会话上下文压缩快照；完整消息仍保留在 harness_conversation_messages 中。
CREATE TABLE harness_conversation_contexts (
    conversation_id VARCHAR(128) NOT NULL,
    summary TEXT NOT NULL,
    compacted_through_sequence INTEGER NOT NULL CHECK (compacted_through_sequence >= 0),
    summary_version INTEGER NOT NULL CHECK (summary_version > 0),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_conversation_contexts PRIMARY KEY (conversation_id),
    CONSTRAINT fk_harness_context_conversation FOREIGN KEY (conversation_id)
        REFERENCES harness_conversations (id)
);

CREATE INDEX idx_harness_conversation_contexts_updated
    ON harness_conversation_contexts (updated_at DESC);
