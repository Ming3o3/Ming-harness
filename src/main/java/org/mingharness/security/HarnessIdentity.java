package org.mingharness.security;

import java.util.Set;
import java.util.stream.Collectors;

/** 当前请求经过认证后的租户、用户和权限快照。 */
public record HarnessIdentity(
        String tenantId,
        String userId,
        Set<String> permissions,
        String authenticationMode
) {

    public HarnessIdentity {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean isApiKey() {
        return "api-key".equalsIgnoreCase(authenticationMode);
    }

    /** API Key 和 OIDC 的权限均来自服务端已验证的身份，不允许请求体覆盖。 */
    public boolean usesTrustedPermissions() {
        return isApiKey() || "oidc".equalsIgnoreCase(authenticationMode);
    }

    public boolean hasPermission(String permission) {
        int separator = permission.indexOf('.');
        return permissions.contains("*")
                || permissions.contains(permission)
                || separator > 0 && permissions.contains(permission.substring(0, separator) + ".*");
    }

    public String permissionsCsv() {
        return permissions.stream().sorted().collect(Collectors.joining(","));
    }
}
