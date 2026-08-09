package org.mingharness.runtime.api;

import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.domain.StepType;
import org.mingharness.context.api.ContextEvidence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StepView(
        String id,
        int sequence,
        StepType type,
        StepStatus status,
        String name,
        String input,
        String output,
        String error,
        int attempt,
        int inputTokens,
        int outputTokens,
        Instant startedAt,
        Instant finishedAt,
        String spanId,
        long durationMs,
        BigDecimal cost,
        List<ContextEvidence> contextEvidence
) {

    /** 兼容早期没有 Span、耗时和成本字段的接口调用方。 */
    public StepView(String id, int sequence, StepType type, StepStatus status, String name,
                    String input, String output, String error, int attempt, int inputTokens,
                    int outputTokens, Instant startedAt, Instant finishedAt) {
        this(id, sequence, type, status, name, input, output, error, attempt, inputTokens,
                outputTokens, startedAt, finishedAt, null, 0, BigDecimal.ZERO, List.of());
    }
}
