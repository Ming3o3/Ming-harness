package org.mingharness.education.api;

import java.time.Instant;

/** 教师或学习者可见的单份课程作业进度投影。 */
public record LearningAssignmentProgressView(
        String assignmentId,
        String title,
        String teacherUserId,
        String learnerUserId,
        String status,
        Instant dueAt,
        String learningGoalId,
        double baselineMastery,
        double currentMastery,
        double targetMastery,
        double masteryProgress,
        long assessmentTotal,
        long correctAssessmentTotal,
        Instant lastAssessmentAt,
        long taskTotal,
        long taskStarted,
        long taskCompleted,
        long taskAwaitingEvidence,
        long taskFailed,
        double masteryGain,
        long runTotal,
        long runWithAssessmentEvidence,
        double runEvidenceCoverageRate,
        long feedbackTotal,
        long feedbackAcknowledged,
        double feedbackAcknowledgementRate,
        long feedbackEvidenceRequests,
        long feedbackRetryRecommendations,
        Instant lastFeedbackAt
) {
}
