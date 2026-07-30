package org.mingharness.evaluation.api;

import org.mingharness.evaluation.EvaluationReport;

import java.math.BigDecimal;
import java.time.Instant;

public record EvaluationReportView(
        String id,
        String tenantId,
        String name,
        String modelName,
        String promptVersion,
        String policyVersion,
        int totalCases,
        int passedCases,
        int failedCases,
        BigDecimal successRate,
        String details,
        Instant createdAt
) {

    public static EvaluationReportView from(EvaluationReport report) {
        return new EvaluationReportView(report.getId(), report.getTenantId(), report.getName(),
                report.getModelName(), report.getPromptVersion(), report.getPolicyVersion(),
                report.getTotalCases(), report.getPassedCases(), report.getFailedCases(),
                report.getSuccessRate(), report.getDetails(), report.getCreatedAt());
    }
}
