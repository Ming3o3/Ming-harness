package org.mingharness.context;

/** 流式文档解析产生的一个有序正文片段；PDF 使用页码，DOCX 页码为 0。 */
public record ParsedDocumentSegment(String text, int pageNumber) {
    public ParsedDocumentSegment {
        text = text == null ? "" : text;
        pageNumber = Math.max(0, pageNumber);
    }
}

