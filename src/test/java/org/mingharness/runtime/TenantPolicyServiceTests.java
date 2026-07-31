package org.mingharness.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mingharness.common.BusinessException;
import org.mingharness.runtime.api.CreateRunRequest;
import org.mingharness.runtime.api.TenantPolicyRequest;
import org.mingharness.runtime.api.TenantPolicyView;
import org.mingharness.runtime.application.RunService;
import org.mingharness.runtime.application.RuntimeLimits;
import org.mingharness.runtime.application.TenantPolicyService;
import org.mingharness.runtime.domain.TenantPolicyAudit;
import org.mingharness.runtime.repository.RunRepository;
import org.mingharness.runtime.repository.TenantPolicyAuditRepository;
import org.mingharness.runtime.repository.TenantPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证租户策略默认值、平台硬上限、审计留痕以及 Run 创建链路的实际约束。 */
@SpringBootTest
class TenantPolicyServiceTests {

    @Autowired
    private TenantPolicyService policyService;
    @Autowired
    private RuntimeLimits runtimeLimits;
    @Autowired
    private TenantPolicyRepository policyRepository;
    @Autowired
    private TenantPolicyAuditRepository auditRepository;
    @Autowired
    private RunRepository runRepository;
    @Autowired
    private RunService runService;

    @AfterEach
    void cleanDatabase() {
        auditRepository.deleteAll();
        policyRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldUsePlatformDefaultsWhenTenantHasNoOverride() {
        TenantPolicyView view = policyService.get("tenant-default");

        assertTrue(view.defaulted());
        assertEquals(runtimeLimits.maxActiveRunsPerTenant(), view.maxActiveRuns());
        assertEquals(runtimeLimits.maxStepsPerRun(), view.maxStepsPerRun());
        assertEquals(runtimeLimits.maxInputLength(), view.maxInputLength());
        assertEquals(0, runtimeLimits.maxBudget().compareTo(view.maxBudget()));
        assertEquals(runtimeLimits.maxCreatesPerMinute(), view.maxCreatesPerMinute());
    }

    @Test
    void shouldPersistOverrideAndRecordResetAudit() {
        TenantPolicyRequest request = new TenantPolicyRequest(3, 5, 2000,
                BigDecimal.valueOf(10.125), 12);

        TenantPolicyView saved = policyService.upsert("tenant-policy", request, "operator-1");
        assertFalse(saved.defaulted());
        assertEquals(3, saved.maxActiveRuns());
        assertEquals(0, BigDecimal.valueOf(10.125).compareTo(saved.maxBudget()));

        TenantPolicyView reset = policyService.reset("tenant-policy", "operator-1");
        assertTrue(reset.defaulted());
        assertEquals(2, auditRepository.count());
        assertEquals("TENANT_POLICY_RESET", auditRepository
                .findByTenantIdOrderByCreatedAtDesc("tenant-policy", org.springframework.data.domain.PageRequest.of(0, 10))
                .get(0).getEventType());
    }

    @Test
    void shouldRejectPolicyThatExceedsPlatformHardLimit() {
        TenantPolicyRequest request = new TenantPolicyRequest(
                runtimeLimits.maxActiveRunsPerTenant() + 1,
                runtimeLimits.maxStepsPerRun(),
                runtimeLimits.maxInputLength(),
                runtimeLimits.maxBudget(),
                runtimeLimits.maxCreatesPerMinute());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> policyService.upsert("tenant-policy", request, "operator-1"));
        assertEquals("TENANT_POLICY_EXCEEDS_PLATFORM_LIMIT", exception.getCode());
        assertEquals(0, policyRepository.count());
    }

    @Test
    void shouldApplyTenantBudgetBeforeRunIsPersisted() {
        policyService.upsert("tenant-budget", new TenantPolicyRequest(
                5, 10, 5000, BigDecimal.ONE, 20), "operator-1");

        BusinessException exception = assertThrows(BusinessException.class, () -> runService.create(
                new CreateRunRequest("tenant-budget", "user-1", "超预算", "输入", "demo.echo",
                        null, null, null, BigDecimal.valueOf(2))));

        assertEquals("BUDGET_EXCEEDED", exception.getCode());
        assertEquals(0, runRepository.countByTenantIdAndStatusIn("tenant-budget",
                java.util.List.of(org.mingharness.runtime.domain.RunStatus.QUEUED,
                        org.mingharness.runtime.domain.RunStatus.RUNNING,
                        org.mingharness.runtime.domain.RunStatus.WAITING_APPROVAL)));
    }
}
