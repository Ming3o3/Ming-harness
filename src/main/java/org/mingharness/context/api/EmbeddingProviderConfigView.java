package org.mingharness.context.api;

import java.time.Instant;

/** 不返回 Embedding API Key 明文。 */
public record EmbeddingProviderConfigView(
        boolean configured,
        boolean enabled,
        boolean ready,
        String source,
        String baseUrl,
        String modelName,
        String modelVersion,
        int dimension,
        boolean apiKeyConfigured,
        String apiKeyHint,
        Instant updatedAt
) {
}
