package org.mingharness.runtime.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 运行时的确定性资源边界，避免单个租户拖垮整个 Harness。 */
@ConfigurationProperties(prefix = "harness.runtime")
public record RuntimeLimits(
        int maxActiveRunsPerTenant,
        int maxStepsPerRun,
        int maxInputLength,
        java.math.BigDecimal maxBudget,
        int modelTimeoutMs,
        int maxContextChars,
        int maxCreatesPerMinute,
        int recoveryTimeoutMs
) {

    public RuntimeLimits {
        if (maxActiveRunsPerTenant < 1) {
            maxActiveRunsPerTenant = 20;
        }
        if (maxStepsPerRun < 1) {
            maxStepsPerRun = 20;
        }
        if (maxInputLength < 1) {
            maxInputLength = 10_000;
        }
        if (maxBudget == null || maxBudget.signum() <= 0) {
            maxBudget = java.math.BigDecimal.valueOf(1_000);
        }
        if (modelTimeoutMs < 1) {
            modelTimeoutMs = 30_000;
        }
        if (maxContextChars < 1) {
            maxContextChars = 4_000;
        }
        if (maxCreatesPerMinute < 1) {
            maxCreatesPerMinute = 60;
        }
        if (recoveryTimeoutMs < 1) {
            recoveryTimeoutMs = 120_000;
        }
    }
}
