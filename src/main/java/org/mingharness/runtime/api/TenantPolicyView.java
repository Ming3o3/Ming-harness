package org.mingharness.runtime.api;

import org.mingharness.runtime.domain.TenantPolicyLimits;

import java.math.BigDecimal;
import java.time.Instant;

/** 租户当前生效的资源策略；defaulted=true 表示尚未设置数据库覆盖策略。 */
public record TenantPolicyView(
        String tenantId,
        int maxActiveRuns,
        int maxStepsPerRun,
        int maxInputLength,
        BigDecimal maxBudget,
        int maxCreatesPerMinute,
        boolean defaulted,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static TenantPolicyView from(String tenantId, TenantPolicyLimits limits, boolean defaulted,
                                        Instant createdAt, Instant updatedAt, long version) {
        return new TenantPolicyView(tenantId, limits.maxActiveRuns(), limits.maxStepsPerRun(),
                limits.maxInputLength(), limits.maxBudget(), limits.maxCreatesPerMinute(),
                defaulted, createdAt, updatedAt, version);
    }
}
