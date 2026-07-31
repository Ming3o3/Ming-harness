package org.mingharness.security;

import jakarta.validation.Valid;
import org.mingharness.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据库 API Key 管理接口。
 *
 * <p>创建响应中的 secret 只出现一次；后续查询、列表和撤销响应永远不会返回明文密钥。</p>
 */
@RestController
@RequestMapping("/api/admin/api-keys")
public class ApiKeyManagementController {

    private final ApiKeyCredentialService credentialService;

    public ApiKeyManagementController(ApiKeyCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    public ApiKeyView create(@Valid @RequestBody CreateApiKeyRequest request) {
        HarnessIdentity identity = requireIdentity();
        assertManagePermission(identity);
        assertTenantScope(identity, request.tenantId());
        return credentialService.create(request, identity.userId());
    }

    @GetMapping
    public List<ApiKeyView> list(@RequestParam(required = false) String tenantId) {
        HarnessIdentity identity = requireIdentity();
        assertReadPermission(identity);
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? identity.tenantId() : tenantId;
        assertTenantScope(identity, effectiveTenant);
        return credentialService.list(effectiveTenant);
    }

    @GetMapping("/{keyId}")
    public ApiKeyView get(@PathVariable String keyId) {
        HarnessIdentity identity = requireIdentity();
        assertReadPermission(identity);
        ApiKeyView view = credentialService.get(keyId);
        assertTenantScope(identity, view.tenantId());
        return view;
    }

    @DeleteMapping("/{keyId}")
    public ApiKeyView revoke(@PathVariable String keyId) {
        HarnessIdentity identity = requireIdentity();
        assertManagePermission(identity);
        ApiKeyView view = credentialService.get(keyId);
        assertTenantScope(identity, view.tenantId());
        return credentialService.revoke(keyId, identity.userId());
    }

    @GetMapping("/audits")
    public List<ApiKeyAuditView> audits(@RequestParam(required = false) String tenantId) {
        HarnessIdentity identity = requireIdentity();
        assertReadPermission(identity);
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? identity.tenantId() : tenantId;
        assertTenantScope(identity, effectiveTenant);
        return credentialService.auditTrail(effectiveTenant);
    }

    private HarnessIdentity requireIdentity() {
        HarnessIdentity identity = HarnessIdentityContext.require();
        if ("local".equalsIgnoreCase(identity.authenticationMode())) {
            return identity;
        }
        return identity;
    }

    private void assertReadPermission(HarnessIdentity identity) {
        if (!"local".equalsIgnoreCase(identity.authenticationMode())
                && !identity.hasPermission("auth.key.read")
                && !identity.hasPermission("auth.key.manage")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "缺少 auth.key.read 权限");
        }
    }

    private void assertManagePermission(HarnessIdentity identity) {
        if (!"local".equalsIgnoreCase(identity.authenticationMode())
                && !identity.hasPermission("auth.key.manage")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "缺少 auth.key.manage 权限");
        }
    }

    private void assertTenantScope(HarnessIdentity identity, String targetTenant) {
        if (!identity.tenantId().equals(targetTenant)
                && !identity.hasPermission("auth.key.cross-tenant")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_SCOPE_DENIED", "当前身份无权管理其他租户的 API Key");
        }
    }
}
