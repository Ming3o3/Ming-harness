package org.mingharness.runtime.domain;

import java.math.BigDecimal;

/** 已经经过平台硬上限校验的租户策略快照。 */
public record TenantPolicyLimits(
        int maxActiveRuns,
        int maxStepsPerRun,
        int maxInputLength,
        BigDecimal maxBudget,
        int maxCreatesPerMinute
) {

    public TenantPolicyLimits {
        if (maxActiveRuns < 1 || maxStepsPerRun < 1 || maxInputLength < 1 || maxCreatesPerMinute < 1) {
            throw new IllegalArgumentException("租户运行策略的整数限制必须大于 0");
        }
        if (maxBudget == null || maxBudget.signum() <= 0) {
            throw new IllegalArgumentException("租户运行预算必须大于 0");
        }
    }
}
