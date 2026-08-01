-- 持久化聊天会话和逐轮消息；每轮消息通过 run_id 关联现有执行 Runtime。
CREATE TABLE harness_conversations (
    id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_conversations PRIMARY KEY (id)
);

CREATE TABLE harness_conversation_messages (
    id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    run_id VARCHAR(128),
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    message_sequence INTEGER NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_conversation_messages PRIMARY KEY (id),
    CONSTRAINT fk_harness_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES harness_conversations (id),
    CONSTRAINT fk_harness_messages_run FOREIGN KEY (run_id)
        REFERENCES harness_runs (id),
    CONSTRAINT uk_harness_message_run_role UNIQUE (run_id, role),
    CONSTRAINT uk_harness_message_conversation_sequence UNIQUE (conversation_id, message_sequence)
);

ALTER TABLE harness_runs ADD COLUMN conversation_id VARCHAR(128);
ALTER TABLE harness_runs ADD CONSTRAINT fk_harness_runs_conversation
    FOREIGN KEY (conversation_id) REFERENCES harness_conversations (id);

CREATE INDEX idx_harness_conversations_tenant_updated
    ON harness_conversations (tenant_id, updated_at DESC);
CREATE INDEX idx_harness_conversations_user_updated
    ON harness_conversations (tenant_id, user_id, updated_at DESC);
CREATE INDEX idx_harness_messages_conversation_sequence
    ON harness_conversation_messages (conversation_id, message_sequence);
CREATE INDEX idx_harness_messages_tenant_created
    ON harness_conversation_messages (tenant_id, created_at DESC);
CREATE INDEX idx_harness_runs_conversation_created
    ON harness_runs (conversation_id, created_at DESC);
