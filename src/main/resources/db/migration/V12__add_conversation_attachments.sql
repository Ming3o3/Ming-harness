-- 聊天附件的文件正文位于受控工作区，此表只保存归属、消息绑定和可审计的相对路径。
CREATE TABLE harness_conversation_attachments (
    id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    message_id VARCHAR(128),
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    workspace_path VARCHAR(1024) NOT NULL,
    media_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_harness_conversation_attachments PRIMARY KEY (id),
    CONSTRAINT fk_harness_attachments_conversation FOREIGN KEY (conversation_id)
        REFERENCES harness_conversations (id),
    CONSTRAINT fk_harness_attachments_message FOREIGN KEY (message_id)
        REFERENCES harness_conversation_messages (id),
    CONSTRAINT uk_harness_attachment_workspace_path UNIQUE (workspace_path)
);

CREATE INDEX idx_harness_attachments_conversation_message
    ON harness_conversation_attachments (conversation_id, message_id);
CREATE INDEX idx_harness_attachments_message_created
    ON harness_conversation_attachments (message_id, created_at);
CREATE INDEX idx_harness_attachments_tenant_created
    ON harness_conversation_attachments (tenant_id, created_at DESC);
