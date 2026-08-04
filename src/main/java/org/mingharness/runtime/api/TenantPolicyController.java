package org.mingharness.runtime.api;

import jakarta.validation.Valid;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.application.TenantPolicyService;
import org.mingharness.security.HarnessIdentity;
import org.mingharness.security.HarnessIdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织运行资源策略管理接口。
 *
 * <p>默认仅能管理自身组织；跨组织运维必须同时拥有普通读写权限和
 * {@code tenant.policy.cross-tenant} 权限，避免一个客户的 API Key 修改其他客户配额。</p>
 */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/policy")
public class TenantPolicyController {

    private final TenantPolicyService policyService;

    public TenantPolicyController(TenantPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public TenantPolicyView get(@PathVariable String tenantId) {
        assertScope(tenantId);
        return policyService.get(tenantId);
    }

    @PutMapping
    public TenantPolicyView put(@PathVariable String tenantId,
                                @Valid @RequestBody TenantPolicyRequest request) {
        HarnessIdentity identity = assertScope(tenantId);
        return policyService.upsert(tenantId, request, identity.userId());
    }

    @DeleteMapping
    public TenantPolicyView reset(@PathVariable String tenantId) {
        HarnessIdentity identity = assertScope(tenantId);
        return policyService.reset(tenantId, identity.userId());
    }

    @GetMapping("/audits")
    public List<TenantPolicyAuditView> auditTrail(@PathVariable String tenantId) {
        assertScope(tenantId);
        return policyService.auditTrail(tenantId);
    }

    private HarnessIdentity assertScope(String tenantId) {
        HarnessIdentity identity = HarnessIdentityContext.require();
        if (!identity.tenantId().equals(tenantId)
                && !identity.hasPermission("tenant.policy.cross-tenant")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TENANT_POLICY_SCOPE_DENIED",
                    "当前身份无权管理其他组织的资源策略");
        }
        return identity;
    }
}
