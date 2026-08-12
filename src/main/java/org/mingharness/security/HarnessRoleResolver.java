package org.mingharness.security;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 将可信身份权限映射为产品工作台角色。
 *
 * <p>正式 API Key/OIDC 身份只根据服务端已验证的权限推断角色；local 模式额外
 * 接受本机演示角色头，方便在没有登录服务的情况下模拟三个用户。</p>
 */
@Component
public class HarnessRoleResolver {

    public Set<HarnessUserRole> resolve(HarnessIdentity identity, String localRole) {
        if (identity == null) return Set.of();
        if ("local".equalsIgnoreCase(identity.authenticationMode())) {
            HarnessUserRole demoRole = parseLocalRole(localRole);
            if (demoRole != null) return Set.of(demoRole);
        }

        Set<HarnessUserRole> roles = new LinkedHashSet<>();
        if (identity.hasPermission("auth.key.manage")
                || identity.hasPermission("tenant.policy.write")
                || identity.hasPermission("ops.read")) {
            roles.add(HarnessUserRole.ADMIN);
        }
        if (identity.hasPermission("education.assign")
                || identity.hasPermission("education.evaluate")
                || identity.hasPermission("context.write")) {
            roles.add(HarnessUserRole.TEACHER);
        }
        if (identity.hasPermission("education.read")
                || identity.hasPermission("education.write")
                || identity.hasPermission("run.create")) {
            roles.add(HarnessUserRole.STUDENT);
        }
        return Set.copyOf(roles);
    }

    public HarnessUserRole primaryRole(Set<HarnessUserRole> roles) {
        if (roles == null || roles.isEmpty()) return null;
        if (roles.contains(HarnessUserRole.ADMIN)) return HarnessUserRole.ADMIN;
        if (roles.contains(HarnessUserRole.TEACHER)) return HarnessUserRole.TEACHER;
        return HarnessUserRole.STUDENT;
    }

    private HarnessUserRole parseLocalRole(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return HarnessUserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
