package org.mingharness.education.api;

import org.mingharness.education.AssessmentAttempt;

import java.time.Instant;

public record AssessmentAttemptView(
        String id,
        String tenantId,
        String userId,
        String runId,
        String stepId,
        String learningGoalId,
        String learnerProfileId,
        String conceptKey,
        boolean correct,
        double observedMastery,
        double masteryBefore,
        double masteryAfter,
        String feedback,
        Instant createdAt
) {

    public static AssessmentAttemptView from(AssessmentAttempt attempt) {
        return new AssessmentAttemptView(attempt.getId(), attempt.getTenantId(), attempt.getUserId(),
                attempt.getRunId(), attempt.getStepId(), attempt.getLearningGoalId(),
                attempt.getLearnerProfileId(), attempt.getConceptKey(), attempt.isCorrect(),
                attempt.getObservedMastery(), attempt.getMasteryBefore(), attempt.getMasteryAfter(),
                attempt.getFeedback(), attempt.getCreatedAt());
    }
}
