package org.mingharness.tool;

import java.util.Map;
import java.util.Set;

public record ToolDefinition(
        String name,
        String description,
        boolean readOnly,
        String riskLevel,
        boolean requiresApproval,
        Map<String, Object> inputSchema,
        Set<String> requiredPermissions,
        int timeoutMs,
        int maxAttempts,
        String networkPolicy,
        Map<String, Object> outputSchema
) {

    /** 兼容早期工具定义，并为工具执行提供保守的默认边界。 */
    public ToolDefinition(String name, String description, boolean readOnly, String riskLevel,
                          boolean requiresApproval, Map<String, Object> inputSchema) {
        this(name, description, readOnly, riskLevel, requiresApproval, inputSchema,
                Set.of(), 30_000, 1, "DENY_EXTERNAL", Map.of());
    }

    public ToolDefinition {
        requiredPermissions = requiredPermissions == null ? Set.of() : Set.copyOf(requiredPermissions);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        timeoutMs = timeoutMs < 1 ? 30_000 : timeoutMs;
        maxAttempts = maxAttempts < 1 ? 1 : maxAttempts;
        networkPolicy = networkPolicy == null || networkPolicy.isBlank() ? "DENY_EXTERNAL" : networkPolicy;
    }
}
