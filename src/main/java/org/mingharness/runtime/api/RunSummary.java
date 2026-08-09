package org.mingharness.runtime.api;

import org.mingharness.runtime.domain.RunStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RunSummary(
        String id,
        String tenantId,
        String userId,
        String title,
        String modelName,
        String promptVersion,
        String policyVersion,
        String input,
        String output,
        String error,
        RunStatus status,
        BigDecimal budget,
        Instant createdAt,
        Instant updatedAt,
        int stepCount,
        String idempotencyKey,
        String traceId,
        long durationMs,
        BigDecimal totalCost,
        boolean agentMode,
        int maxTurns,
        String workspaceId,
        boolean educationMode,
        String educationSubject,
        String educationGradeLevel,
        String educationCurriculumVersion,
        String educationLearnerProfileId,
        String educationPedagogicalMode
) {

    /** 兼容早期只返回幂等键的调用方。 */
    public RunSummary(String id, String tenantId, String userId, String title, String modelName,
                      String promptVersion, String policyVersion, String input, String output,
                      String error, RunStatus status, BigDecimal budget, Instant createdAt,
                      Instant updatedAt, int stepCount, String idempotencyKey) {
        this(id, tenantId, userId, title, modelName, promptVersion, policyVersion, input, output,
                error, status, budget, createdAt, updatedAt, stepCount, idempotencyKey,
                null, 0, BigDecimal.ZERO, false, 1, null,
                false, null, null, null, null, null);
    }

    /** 兼容上一版已经携带追踪、耗时和成本字段的调用方。 */
    public RunSummary(String id, String tenantId, String userId, String title, String modelName,
                      String promptVersion, String policyVersion, String input, String output,
                      String error, RunStatus status, BigDecimal budget, Instant createdAt,
                      Instant updatedAt, int stepCount, String idempotencyKey, String traceId,
                      long durationMs, BigDecimal totalCost) {
        this(id, tenantId, userId, title, modelName, promptVersion, policyVersion, input, output,
                error, status, budget, createdAt, updatedAt, stepCount, idempotencyKey,
                traceId, durationMs, totalCost, false, 1, null,
                false, null, null, null, null, null);
    }
}
