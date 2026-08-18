package org.mingharness.security;

import java.time.Instant;
import java.util.List;

/** 用户直接授权的安全视图，不包含任何凭证信息。 */
public record UserPermissionView(
        String id,
        String tenantId,
        String userId,
        List<String> permissions,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy
) {
}
