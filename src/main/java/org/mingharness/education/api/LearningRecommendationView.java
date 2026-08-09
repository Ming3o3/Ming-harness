package org.mingharness.education.api;

import java.time.Instant;

/** 将学习状态翻译成下一步可执行的学习动作。 */
public record LearningRecommendationView(
        String learningGoalId,
        String learningGoalTitle,
        String goalStatus,
        String conceptKey,
        double currentMastery,
        double baselineMastery,
        double targetMastery,
        double progressRatio,
        long attemptCount,
        long correctAttemptCount,
        Instant lastAssessmentAt,
        String reviewPlanId,
        String reviewPlanStatus,
        Instant nextReviewAt,
        int reviewIntervalDays,
        long reviewCount,
        long successfulReviewCount,
        String nextActionType,
        String nextActionTitle,
        String nextActionPrompt,
        String rationale
) {

    /** 兼容尚未接入保持度复习计划的旧测试和扩展调用方。 */
    public LearningRecommendationView(String learningGoalId, String learningGoalTitle, String goalStatus,
                                      String conceptKey, double currentMastery, double baselineMastery,
                                      double targetMastery, double progressRatio, long attemptCount,
                                      long correctAttemptCount, Instant lastAssessmentAt,
                                      String nextActionType, String nextActionTitle, String nextActionPrompt,
                                      String rationale) {
        this(learningGoalId, learningGoalTitle, goalStatus, conceptKey, currentMastery, baselineMastery,
                targetMastery, progressRatio, attemptCount, correctAttemptCount, lastAssessmentAt,
                null, null, null, 0, 0, 0, nextActionType, nextActionTitle, nextActionPrompt, rationale);
    }
}
