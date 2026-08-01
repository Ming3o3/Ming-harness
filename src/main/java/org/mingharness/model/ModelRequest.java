package org.mingharness.model;

import java.util.List;

public record ModelRequest(
        String input,
        String model,
        String promptVersion,
        List<ModelToolDefinition> tools
) {

    /** 保留现有模型调用方，不启用 Tool Call。 */
    public ModelRequest(String input, String model, String promptVersion) {
        this(input, model, promptVersion, List.of());
    }

    public ModelRequest {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
