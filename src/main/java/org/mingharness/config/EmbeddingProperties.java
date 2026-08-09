package org.mingharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** embedding 供应商配置，与聊天模型配置分离，避免不同模型的权限和维度被混用。 */
@ConfigurationProperties(prefix = "harness.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        String modelVersion,
        int dimension,
        int batchSize,
        int maxInputChars,
        int maxInputTokens,
        int maxResponseChars,
        int maxAttempts,
        long retryBackoffMs,
        long timeoutMs
) {

    /** 兼容已有测试和本地调用，默认使用 v1 缓存版本。 */
    public EmbeddingProperties(boolean enabled, String baseUrl, String apiKey, String model,
                               int dimension, int batchSize, int maxInputChars, int maxResponseChars,
                               int maxAttempts, long retryBackoffMs, long timeoutMs) {
        this(enabled, baseUrl, apiKey, model, "v1", dimension, batchSize, maxInputChars,
                8_192, maxResponseChars, maxAttempts, retryBackoffMs, timeoutMs);
    }

    @ConstructorBinding
    public EmbeddingProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
        model = model == null || model.isBlank() ? "text-embedding-3-small" : model.trim();
        modelVersion = modelVersion == null || modelVersion.isBlank() ? "v1" : modelVersion.trim();
        modelVersion = modelVersion.length() > 128 ? modelVersion.substring(0, 128) : modelVersion;
        dimension = Math.min(8_192, Math.max(1, dimension));
        batchSize = Math.min(128, Math.max(1, batchSize));
        maxInputChars = Math.min(1_000_000, Math.max(128, maxInputChars));
        maxInputTokens = Math.min(1_000_000, Math.max(1, maxInputTokens));
        maxResponseChars = Math.min(10_000_000, Math.max(1_024, maxResponseChars));
        maxAttempts = Math.min(5, Math.max(1, maxAttempts));
        retryBackoffMs = Math.min(10_000, Math.max(0, retryBackoffMs));
        timeoutMs = Math.min(120_000, Math.max(1_000, timeoutMs));
    }
}
