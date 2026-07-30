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
        String idempotencyKey
) {
}
