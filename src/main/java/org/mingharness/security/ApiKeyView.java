package org.mingharness.security;

import java.time.Instant;
import java.util.List;

/** API Key 安全视图；secret 仅在创建响应中非空。 */
public record ApiKeyView(
        String id,
        String keyPrefix,
        String tenantId,
        String userId,
        List<String> permissions,
        ApiKeyStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokedBy,
        String secret
) {
}
