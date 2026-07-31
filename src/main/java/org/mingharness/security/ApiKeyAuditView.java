package org.mingharness.security;

import java.time.Instant;

/** 管理控制台使用的 API Key 生命周期审计记录。 */
public record ApiKeyAuditView(
        String id,
        String keyId,
        String tenantId,
        String actorId,
        String eventType,
        String details,
        Instant createdAt
) {
}
