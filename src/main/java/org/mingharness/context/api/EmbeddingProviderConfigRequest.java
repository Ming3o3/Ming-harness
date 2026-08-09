package org.mingharness.context.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** API Key 留空表示保留当前密钥；clearApiKey=true 才会删除已保存密钥。 */
public record EmbeddingProviderConfigRequest(
        Boolean enabled,
        @Size(max = 512, message = "Embedding API 地址不能超过 512 个字符") String baseUrl,
        @Size(max = 128, message = "Embedding 模型不能超过 128 个字符") String modelName,
        @Size(max = 128, message = "Embedding 模型版本不能超过 128 个字符") String modelVersion,
        Integer dimension,
        @Size(max = 1000, message = "Embedding API Key 不能超过 1000 个字符") String apiKey,
        Boolean clearApiKey
) {

    @AssertTrue(message = "启用 Embedding 时必须提供 API 地址")
    public boolean hasBaseUrl() {
        return !Boolean.TRUE.equals(enabled) || (baseUrl != null && !baseUrl.isBlank());
    }
}
