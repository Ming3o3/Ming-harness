package org.mingharness.runtime.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

/** 租户资源策略的完整替换请求；数值只能收紧，不能突破平台全局硬上限。 */
public record TenantPolicyRequest(
        @Min(value = 1, message = "最大活动 Run 数必须大于 0") int maxActiveRuns,
        @Min(value = 1, message = "单次最大步骤数必须大于 0") int maxStepsPerRun,
        @Min(value = 1, message = "最大输入长度必须大于 0") int maxInputLength,
        @NotNull(message = "最大预算不能为空")
        @DecimalMin(value = "0.000001", message = "最大预算必须大于 0") BigDecimal maxBudget,
        @Min(value = 1, message = "每分钟最大创建数必须大于 0") int maxCreatesPerMinute,
        Set<String> allowedTools
) {

    /** 兼容早期管理调用方；未提供白名单时保持允许所有已注册工具。 */
    public TenantPolicyRequest(int maxActiveRuns, int maxStepsPerRun, int maxInputLength,
                               BigDecimal maxBudget, int maxCreatesPerMinute) {
        this(maxActiveRuns, maxStepsPerRun, maxInputLength, maxBudget, maxCreatesPerMinute, Set.of());
    }
}
