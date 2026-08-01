package org.mingharness.model;

/** 模型提出的一次工具调用；调用参数仍需经过 Harness JSON Schema 和策略校验。 */
public record ModelToolCall(
        String id,
        String name,
        String arguments
) {
}
