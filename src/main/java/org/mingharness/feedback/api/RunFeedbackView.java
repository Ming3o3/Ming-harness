package org.mingharness.feedback.api;

import org.mingharness.feedback.RunFeedback;

import java.time.Instant;

public record RunFeedbackView(
        String id,
        String runId,
        String messageId,
        String rating,
        String reasonCode,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
    public static RunFeedbackView from(RunFeedback feedback) {
        return new RunFeedbackView(feedback.getId(), feedback.getRunId(), feedback.getMessageId(),
                feedback.getRating(), feedback.getReasonCode(), feedback.getNote(),
                feedback.getCreatedAt(), feedback.getUpdatedAt());
    }
}
