package org.mingharness.dashboard;

import java.math.BigDecimal;

public record RunDashboardSummary(
        long total,
        long queued,
        long running,
        long waitingApproval,
        long succeeded,
        long failed,
        long cancelled,
        long inputTokens,
        long outputTokens,
        long totalDurationMs,
        BigDecimal totalCost
) {

    /** 兼容早期只返回状态和 Token 统计的调用方。 */
    public RunDashboardSummary(long total, long queued, long running, long waitingApproval,
                               long succeeded, long failed, long cancelled,
                               long inputTokens, long outputTokens) {
        this(total, queued, running, waitingApproval, succeeded, failed, cancelled,
                inputTokens, outputTokens, 0, BigDecimal.ZERO);
    }
}
