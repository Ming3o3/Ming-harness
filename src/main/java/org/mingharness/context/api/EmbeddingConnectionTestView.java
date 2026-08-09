package org.mingharness.context.api;

public record EmbeddingConnectionTestView(
        boolean success,
        String status,
        String message,
        String modelName,
        int dimension,
        long latencyMs,
        String errorCode
) {
}
