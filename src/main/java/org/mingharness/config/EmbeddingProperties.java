package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** embedding 供应商配置，与聊天模型配置分离，避免不同模型的权限和维度被混用。 */
@ConfigurationProperties(prefix = "harness.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int dimension,
        int batchSize,
        int maxInputChars,
        int maxResponseChars,
        int maxAttempts,
        long retryBackoffMs,
        long timeoutMs
) {

    public EmbeddingProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
        model = model == null || model.isBlank() ? "text-embedding-3-small" : model.trim();
        dimension = Math.min(8_192, Math.max(1, dimension));
        batchSize = Math.min(128, Math.max(1, batchSize));
        maxInputChars = Math.min(1_000_000, Math.max(128, maxInputChars));
        maxResponseChars = Math.min(10_000_000, Math.max(1_024, maxResponseChars));
        maxAttempts = Math.min(5, Math.max(1, maxAttempts));
        retryBackoffMs = Math.min(10_000, Math.max(0, retryBackoffMs));
        timeoutMs = Math.min(120_000, Math.max(1_000, timeoutMs));
    }
}
