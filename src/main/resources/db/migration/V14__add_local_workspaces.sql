-- 本地桌面工作区：根目录仅保存加密密文，绝对路径不会出现在业务 API 或聊天记录中。
CREATE TABLE harness_local_workspaces (
    id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    root_path_ciphertext TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_harness_local_workspaces PRIMARY KEY (id),
    CONSTRAINT uk_harness_local_workspace_owner_name UNIQUE (tenant_id, user_id, display_name)
);

ALTER TABLE harness_conversations ADD COLUMN workspace_id VARCHAR(128);
ALTER TABLE harness_runs ADD COLUMN workspace_id VARCHAR(128);

CREATE INDEX idx_harness_local_workspaces_owner_updated
    ON harness_local_workspaces (tenant_id, user_id, updated_at DESC);
CREATE INDEX idx_harness_conversations_workspace_updated
    ON harness_conversations (workspace_id, updated_at DESC);
CREATE INDEX idx_harness_runs_workspace_created
    ON harness_runs (workspace_id, created_at DESC);
