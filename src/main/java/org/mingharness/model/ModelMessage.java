package org.mingharness.model;

import java.util.List;

/** OpenAI 兼容模型的结构化对话消息。 */
public record ModelMessage(
        String role,
        String content,
        List<ModelToolCall> toolCalls,
        String toolCallId,
        String reasoningContent
) {

    public ModelMessage {
        role = role == null || role.isBlank() ? "user" : role;
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolCallId = toolCallId == null ? "" : toolCallId;
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    /** 兼容旧的四字段消息构造方式。 */
    public ModelMessage(String role, String content, List<ModelToolCall> toolCalls,
                        String toolCallId) {
        this(role, content, toolCalls, toolCallId, "");
    }

    public static ModelMessage system(String content) {
        return new ModelMessage("system", content, List.of(), "", "");
    }

    public static ModelMessage user(String content) {
        return new ModelMessage("user", content, List.of(), "", "");
    }

    public static ModelMessage assistant(String content, List<ModelToolCall> toolCalls) {
        return new ModelMessage("assistant", content, toolCalls, "", "");
    }

    public static ModelMessage assistant(String content, String reasoningContent,
                                         List<ModelToolCall> toolCalls) {
        return new ModelMessage("assistant", content, toolCalls, "", reasoningContent);
    }

    public static ModelMessage tool(String toolCallId, String content) {
        return new ModelMessage("tool", content, List.of(), toolCallId, "");
    }
}
