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
        String nextActionType,
        String nextActionTitle,
        String nextActionPrompt,
        String rationale
) {
}
