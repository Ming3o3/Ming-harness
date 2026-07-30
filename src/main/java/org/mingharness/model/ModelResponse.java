package org.mingharness.model;

import java.math.BigDecimal;

public record ModelResponse(
        String content,
        String model,
        String promptVersion,
        int inputTokens,
        int outputTokens,
        BigDecimal cost
) {

    public ModelResponse(String content, String model, String promptVersion,
                         int inputTokens, int outputTokens) {
        this(content, model, promptVersion, inputTokens, outputTokens, BigDecimal.ZERO);
    }

    public ModelResponse {
        cost = cost == null ? BigDecimal.ZERO : cost;
    }
}
