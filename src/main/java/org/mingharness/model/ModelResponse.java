package org.mingharness.model;

public record ModelResponse(
        String content,
        String model,
        String promptVersion,
        int inputTokens,
        int outputTokens
) {
}
