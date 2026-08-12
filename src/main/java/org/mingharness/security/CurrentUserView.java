package org.mingharness.security;

import java.util.List;

/** 当前请求身份和工作台角色的脱敏视图。 */
public record CurrentUserView(
        String tenantId,
        String userId,
        String authenticationMode,
        String primaryRole,
        List<String> roles,
        List<String> permissions,
        boolean localDemo
) {
}
