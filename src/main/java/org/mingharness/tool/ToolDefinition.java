package org.mingharness.tool;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        boolean readOnly,
        String riskLevel,
        Map<String, Object> inputSchema
) {
}
