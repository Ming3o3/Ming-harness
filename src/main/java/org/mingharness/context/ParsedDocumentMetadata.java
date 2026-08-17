package org.mingharness.context;

/** 流式解析完成后的文档元数据，不持有全文正文。 */
public record ParsedDocumentMetadata(
        String originalName,
        String mediaType,
        String format,
        int contentCharCount,
        int pageCount
) {
}
