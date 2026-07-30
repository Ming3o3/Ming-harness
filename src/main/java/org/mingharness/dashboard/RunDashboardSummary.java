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
}
