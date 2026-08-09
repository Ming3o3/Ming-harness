package org.mingharness.education.api;

import org.mingharness.education.LearningReviewPlan;

import java.time.Instant;

/** 保持度计划的可读投影，供学习者和教师判断下一次复习是否到期。 */
public record LearningReviewPlanView(
        String id,
        String learningGoalId,
        String learnerProfileId,
        String conceptKey,
        String status,
        int reviewCount,
        int successfulReviewCount,
        int intervalDays,
        Instant nextReviewAt,
        Instant lastReviewedAt,
        Boolean lastReviewCorrect,
        Instant createdAt,
        Instant updatedAt
) {

    public static LearningReviewPlanView from(LearningReviewPlan plan) {
        if (plan == null) return null;
        return new LearningReviewPlanView(plan.getId(), plan.getLearningGoalId(), plan.getLearnerProfileId(),
                plan.getConceptKey(), plan.getStatus().name(), plan.getReviewCount(),
                plan.getSuccessfulReviewCount(), plan.getIntervalDays(), plan.getNextReviewAt(),
                plan.getLastReviewedAt(), plan.getLastReviewCorrect(), plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
