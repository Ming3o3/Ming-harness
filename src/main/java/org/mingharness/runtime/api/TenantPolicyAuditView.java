package org.mingharness.runtime.api;

import java.time.Instant;

/** 面向管理控制台的组织策略变更记录。 */
public record TenantPolicyAuditView(
        String id,
        String tenantId,
        String actorId,
        String eventType,
        String details,
        Instant createdAt
) {
}
