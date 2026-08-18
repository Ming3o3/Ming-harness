package org.mingharness.security;

import jakarta.validation.Valid;
import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员直接维护租户内用户权限，不要求轮换或重新发放 API Key。 */
@RestController
@RequestMapping("/api/admin/user-permissions")
public class UserPermissionManagementController {

    private final HarnessUserPermissionService service;

    public UserPermissionManagementController(HarnessUserPermissionService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserPermissionView> list(@RequestParam(required = false) String tenantId) {
        HarnessIdentity identity = requireIdentity();
        assertReadPermission(identity);
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? identity.tenantId() : tenantId.trim();
        assertTenantScope(identity, effectiveTenant);
        return service.list(effectiveTenant);
    }

    @GetMapping("/{userId}")
    public UserPermissionView get(@PathVariable String userId,
                                  @RequestParam(required = false) String tenantId) {
        HarnessIdentity identity = requireIdentity();
        assertReadPermission(identity);
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? identity.tenantId() : tenantId.trim();
        assertTenantScope(identity, effectiveTenant);
        return service.get(effectiveTenant, userId);
    }

    @PutMapping
    public UserPermissionView assign(@Valid @RequestBody AssignUserPermissionsRequest request) {
        HarnessIdentity identity = requireIdentity();
        assertManagePermission(identity);
        assertTenantScope(identity, request.tenantId());
        return service.assign(request, identity.userId());
    }

    @DeleteMapping
    public void clear(@RequestParam String tenantId, @RequestParam String userId) {
        HarnessIdentity identity = requireIdentity();
        assertManagePermission(identity);
        assertTenantScope(identity, tenantId);
        service.clear(tenantId, userId);
    }

    private HarnessIdentity requireIdentity() {
        return HarnessIdentityContext.require();
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

    private void assertManagePermission(HarnessIdentity identity) {
        if (!"local".equalsIgnoreCase(identity.authenticationMode())
                && !identity.hasPermission("auth.user.manage")
                && !identity.hasPermission("auth.key.manage")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "缺少 auth.user.manage 权限");
        }
    }

    private void assertTenantScope(HarnessIdentity identity, String targetTenant) {
        if (targetTenant == null || targetTenant.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "组织不能为空");
        }
        if (!identity.tenantId().equals(targetTenant)
                && !identity.hasPermission("auth.user.cross-tenant")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_SCOPE_DENIED", "当前身份无权管理其他组织的用户权限");
        }
    }
}
