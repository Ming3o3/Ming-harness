package org.mingharness.model;

import java.math.BigDecimal;
import java.util.List;

public record ModelResponse(
        String content,
        String model,
        String promptVersion,
        int inputTokens,
        int outputTokens,
        BigDecimal cost,
        List<ModelToolCall> toolCalls,
        String reasoningContent
) {

    public ModelResponse(String content, String model, String promptVersion,
                         int inputTokens, int outputTokens) {
        this(content, model, promptVersion, inputTokens, outputTokens, BigDecimal.ZERO, List.of(), "");
    }

    public ModelResponse(String content, String model, String promptVersion,
                         int inputTokens, int outputTokens, BigDecimal cost) {
        this(content, model, promptVersion, inputTokens, outputTokens, cost, List.of(), "");
    }

    /** 兼容未携带思考内容的旧模型网关调用方。 */
    public ModelResponse(String content, String model, String promptVersion,
                         int inputTokens, int outputTokens, BigDecimal cost,
                         List<ModelToolCall> toolCalls) {
        this(content, model, promptVersion, inputTokens, outputTokens, cost, toolCalls, "");
    }

    public ModelResponse {
        content = content == null ? "" : content;
        cost = cost == null ? BigDecimal.ZERO : cost;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
