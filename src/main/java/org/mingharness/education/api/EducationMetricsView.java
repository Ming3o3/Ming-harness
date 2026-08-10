package org.mingharness.education.api;

/** 当前租户和用户可见的教育业务闭环指标。 */
public record EducationMetricsView(
        long assignmentTotal,
        long assignmentAccepted,
        long assignmentCompleted,
        double assignmentAcceptanceRate,
        double assignmentCompletionRate,
        long taskTotal,
        long taskStarted,
        long taskCompleted,
        long taskAwaitingEvidence,
        long taskFailed,
        long taskRetryCount,
        double taskStartRate,
        double taskCompletionRate,
        long taskEvidenceCovered,
        double taskEvidenceCoverageRate,
        long notificationTotal,
        long notificationSeen,
        long notificationRead,
        double notificationReadRate,
        long assessmentTotal,
        long formativeAssessmentTotal,
        long reviewAssessmentTotal,
        long correctAssessmentTotal,
        double assessmentAccuracyRate
) {
}
