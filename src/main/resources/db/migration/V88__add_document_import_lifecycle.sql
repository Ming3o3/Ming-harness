ALTER TABLE harness_context_documents
    ADD COLUMN IF NOT EXISTS import_status VARCHAR(32) NOT NULL DEFAULT 'READY';

ALTER TABLE harness_context_documents
    ADD COLUMN IF NOT EXISTS import_error VARCHAR(2000);

ALTER TABLE harness_context_documents
    ADD COLUMN IF NOT EXISTS content_char_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE harness_context_documents
    ADD COLUMN IF NOT EXISTS page_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE harness_context_documents
    ADD COLUMN IF NOT EXISTS import_source_path VARCHAR(2000);

ALTER TABLE harness_context_documents
    ADD COLUMN IF NOT EXISTS import_source_name VARCHAR(512);

UPDATE harness_context_documents
   SET content_char_count = COALESCE(char_length(content), 0)
 WHERE content_char_count = 0;

CREATE INDEX IF NOT EXISTS idx_harness_context_documents_import_status
    ON harness_context_documents (tenant_id, import_status, created_at DESC);

COMMENT ON COLUMN harness_context_documents.import_status IS '文档解析导入状态：PROCESSING、READY 或 FAILED';
COMMENT ON COLUMN harness_context_documents.import_error IS '文档解析失败时展示给上传者的脱敏错误信息';
COMMENT ON COLUMN harness_context_documents.content_char_count IS '解析后的正文字符数';
COMMENT ON COLUMN harness_context_documents.page_count IS 'PDF 页数，DOCX 为 0';
COMMENT ON COLUMN harness_context_documents.import_source_path IS '异步导入暂存文件路径，处理完成后清空';
COMMENT ON COLUMN harness_context_documents.import_source_name IS '异步导入文件原始名称，用于解析格式校验';
