package org.mingharness.model;

import java.util.Map;

/** 发给模型供应商的工具契约，不携带 Harness 内部权限和密钥信息。 */
public record ModelToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {

    public ModelToolDefinition {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
