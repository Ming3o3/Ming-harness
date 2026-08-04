package org.mingharness.runtime.domain;

import java.math.BigDecimal;
import java.util.Set;

/** 已经经过平台硬上限校验的组织策略快照。 */
public record TenantPolicyLimits(
        int maxActiveRuns,
        int maxStepsPerRun,
        int maxInputLength,
        BigDecimal maxBudget,
        int maxCreatesPerMinute,
        Set<String> allowedTools
) {

    /** 兼容没有配置工具白名单的旧策略，空集合表示允许注册表中的所有工具。 */
    public TenantPolicyLimits(int maxActiveRuns, int maxStepsPerRun, int maxInputLength,
                              BigDecimal maxBudget, int maxCreatesPerMinute) {
        this(maxActiveRuns, maxStepsPerRun, maxInputLength, maxBudget, maxCreatesPerMinute, Set.of());
    }

    public TenantPolicyLimits {
        if (maxActiveRuns < 1 || maxStepsPerRun < 1 || maxInputLength < 1 || maxCreatesPerMinute < 1) {
            throw new IllegalArgumentException("组织运行策略的整数限制必须大于 0");
        }
        if (maxBudget == null || maxBudget.signum() <= 0) {
            throw new IllegalArgumentException("组织运行预算必须大于 0");
        }
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
    }

    /** 空白名单表示未启用限制；否则只允许列出的工具名称。 */
    public boolean allowsTool(String toolName) {
        return allowedTools.isEmpty() || allowedTools.contains(toolName);
    }
}
