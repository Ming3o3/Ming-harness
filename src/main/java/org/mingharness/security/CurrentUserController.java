package org.mingharness.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** 为前端工作台提供当前用户身份摘要；不返回任何密钥或敏感凭证。 */
@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

    private final HarnessRoleResolver roleResolver;

    public CurrentUserController(HarnessRoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    @GetMapping
    public CurrentUserView current(
            @RequestHeader(value = "X-Harness-Role", required = false) String localRole) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        Set<HarnessUserRole> roles = roleResolver.resolve(identity, localRole);
        List<String> roleNames = roles.stream().map(Enum::name).sorted().toList();
        HarnessUserRole primary = roleResolver.primaryRole(roles);
        List<String> permissions = identity.permissions().stream().sorted(Comparator.naturalOrder()).toList();
        return new CurrentUserView(identity.tenantId(), identity.userId(), identity.authenticationMode(),
                primary == null ? null : primary.name(), roleNames, permissions,
                "local".equalsIgnoreCase(identity.authenticationMode()));
    }
}
