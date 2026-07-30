package org.mingharness.runtime.api;

import org.mingharness.runtime.domain.StepStatus;
import org.mingharness.runtime.domain.StepType;

import java.time.Instant;

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
        Instant startedAt,
        Instant finishedAt
) {
}
