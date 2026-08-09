package org.mingharness.education.api;

import org.mingharness.education.LearningGoal;
import org.mingharness.education.LearningGoalStatus;

import java.time.Instant;

public record LearningGoalView(
        String id,
        String tenantId,
        String userId,
        String learnerProfileId,
        String title,
        String conceptKey,
        double baselineMastery,
        double targetMastery,
        LearningGoalStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {

    public static LearningGoalView from(LearningGoal goal) {
        return new LearningGoalView(goal.getId(), goal.getTenantId(), goal.getUserId(),
                goal.getLearnerProfileId(), goal.getTitle(), goal.getConceptKey(),
                goal.getBaselineMastery(), goal.getTargetMastery(), goal.getStatus(),
                goal.getCreatedAt(), goal.getUpdatedAt(), goal.getCompletedAt());
    }
}
