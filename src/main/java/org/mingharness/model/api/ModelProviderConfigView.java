package org.mingharness.model.api;

import java.time.Instant;

/** 不返回 API Key 明文，只返回是否配置和不可逆掩码。 */
public record ModelProviderConfigView(
        boolean configured,
        boolean enabled,
        String source,
        String baseUrl,
        String modelName,
        boolean apiKeyConfigured,
        String apiKeyHint,
        Instant updatedAt
) {
}
