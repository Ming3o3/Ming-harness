package org.mingharness.model.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** API Key 留空表示保留已保存密钥；需要删除时显式传 clearApiKey=true。 */
public record UpdateModelProviderConfigRequest(
        Boolean enabled,
        @Size(max = 512, message = "模型 API 地址不能超过 512 个字符") String baseUrl,
        @Size(max = 128, message = "模型名称不能超过 128 个字符") String modelName,
        @Size(max = 1000, message = "模型 API Key 不能超过 1000 个字符") String apiKey,
        Boolean clearApiKey
) {

    @AssertTrue(message = "模型配置必须提供 API 地址")
    public boolean hasBaseUrl() {
        return !Boolean.TRUE.equals(enabled) || (baseUrl != null && !baseUrl.isBlank());
    }
}
