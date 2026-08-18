package org.mingharness.security;

import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员用于选择租户用户的安全目录接口。 */
@RestController
@RequestMapping("/api/admin/users")
public class UserDirectoryManagementController {

    private final HarnessUserDirectoryService service;

    public UserDirectoryManagementController(HarnessUserDirectoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserDirectoryView> list(@RequestParam(required = false) String tenantId) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        assertReadPermission(identity);
        String effectiveTenant = tenantId == null || tenantId.isBlank()
                ? identity.tenantId() : tenantId.trim();
        assertTenantScope(identity, effectiveTenant);
        String currentUser = identity.tenantId().equals(effectiveTenant) ? identity.userId() : null;
        return service.list(effectiveTenant, currentUser);
    }

    private void assertReadPermission(HarnessIdentity identity) {
        if (!"local".equalsIgnoreCase(identity.authenticationMode())
                && !identity.hasPermission("auth.user.read")
                && !identity.hasPermission("auth.user.manage")
                && !identity.hasPermission("auth.key.read")
                && !identity.hasPermission("auth.key.manage")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "缺少 auth.user.read 权限");
        }
    }

    private void assertTenantScope(HarnessIdentity identity, String targetTenant) {
        if (targetTenant == null || targetTenant.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "组织不能为空");
        }
        if (!identity.tenantId().equals(targetTenant)
                && !identity.hasPermission("auth.user.cross-tenant")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_SCOPE_DENIED", "当前身份无权查看其他组织的用户");
        }
    }
}
