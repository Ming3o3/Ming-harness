package org.mingharness.evaluation.api;

import org.mingharness.evaluation.ContextRetrievalEvaluationReport;

import java.math.BigDecimal;
import java.time.Instant;

public record ContextRetrievalEvaluationView(
        String id,
        String tenantId,
        String name,
        int topK,
        int totalCases,
        int hitCases,
        BigDecimal hitRateAtK,
        BigDecimal recallAtK,
        BigDecimal mrr,
        int contextCases,
        int contextHitCases,
        BigDecimal contextHitRate,
        String details,
        Instant createdAt
) {

    public static ContextRetrievalEvaluationView from(ContextRetrievalEvaluationReport report) {
        return new ContextRetrievalEvaluationView(report.getId(), report.getTenantId(), report.getName(),
                report.getTopK(), report.getTotalCases(), report.getHitCases(), report.getHitRateAtK(),
                report.getRecallAtK(), report.getMrr(), report.getContextCases(), report.getContextHitCases(),
                report.getContextHitRate(), report.getDetails(), report.getCreatedAt());
    }
}
