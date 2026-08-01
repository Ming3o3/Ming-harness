-- 目录附件以一条记录代表整个导入根目录，避免目录内文件数量占满每轮消息附件上限。
ALTER TABLE harness_conversation_attachments
    ADD COLUMN directory BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE harness_conversation_attachments
    ADD COLUMN file_count INTEGER NOT NULL DEFAULT 1;
