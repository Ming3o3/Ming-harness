package org.mingharness.model;

import java.util.List;

public record ModelRequest(
        String input,
        String model,
        String promptVersion,
        List<ModelToolDefinition> tools,
        List<ModelMessage> messages,
        String tenantId,
        String userId
) {

    /** 保留现有模型调用方，不启用 Tool Call。 */
    public ModelRequest(String input, String model, String promptVersion) {
        this(input, model, promptVersion, List.of(), List.of(), null, null);
    }

    public ModelRequest(String input, String model, String promptVersion,
                        List<ModelToolDefinition> tools) {
        this(input, model, promptVersion, tools, List.of(), null, null);
    }

    public ModelRequest(String input, String model, String promptVersion,
                        List<ModelToolDefinition> tools, List<ModelMessage> messages) {
        this(input, model, promptVersion, tools, messages, null, null);
    }

    public ModelRequest(String input, String model, String promptVersion,
                        List<ModelToolDefinition> tools, List<ModelMessage> messages,
                        String tenantId, String userId) {
        this.input = input;
        this.model = model;
        this.promptVersion = promptVersion;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.tenantId = tenantId;
        this.userId = userId;
    }
}
