package org.mingharness.evaluation.api;

import org.mingharness.evaluation.EvaluationCase;
import org.mingharness.runtime.domain.RunScenario;

import java.math.BigDecimal;
import java.time.Instant;

public record EvaluationCaseView(
        String id,
        String tenantId,
        String ownerUserId,
        String sourceRunId,
        String name,
        String input,
        String toolName,
        String expectedContains,
        BigDecimal budget,
        RunScenario scenario,
        Instant createdAt,
        Instant updatedAt
) {
    public static EvaluationCaseView from(EvaluationCase item) {
        return new EvaluationCaseView(item.getId(), item.getTenantId(), item.getOwnerUserId(),
                item.getSourceRunId(), item.getName(), item.getInput(), item.getToolName(),
                item.getExpectedContains(), item.getBudget(), item.getScenario(),
                item.getCreatedAt(), item.getUpdatedAt());
    }
}
