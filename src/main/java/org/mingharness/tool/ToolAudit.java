package org.mingharness.tool;

/** 工具可选返回的结构化审计摘要；正文输出不会自动复制进审计链。 */
public record ToolAudit(
        String eventType,
        String message,
        String metadata
) {
}
