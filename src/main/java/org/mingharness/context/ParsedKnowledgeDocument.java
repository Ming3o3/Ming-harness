package org.mingharness.context;

/** 文件解析后的纯文本和来源元数据；原始二进制内容不会写入知识库。 */
public record ParsedKnowledgeDocument(
        String originalName,
        String mediaType,
        String format,
        String text,
        int pageCount
) {
}
