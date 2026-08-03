package org.mingharness.runtime.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 为同一 Run 内的 Agent Tool Call 生成稳定比较键。 */
final class AgentToolCallKey {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AgentToolCallKey() {
    }

    static String of(String name, String arguments) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedArguments = normalizeArguments(arguments);
        return normalizedName + "\n" + normalizedArguments;
    }

    private static String normalizeArguments(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) return raw;
        try {
            // JsonNode.toString() 去除空白差异；参数的 schema 校验仍是最终安全边界。
            return OBJECT_MAPPER.reader().readTree(raw).toString();
        } catch (JacksonException exception) {
            // 无效参数会在 ToolInputValidator 中失败，这里仍保留原值以避免把不同坏请求混为一谈。
            return raw;
        }
    }
}
