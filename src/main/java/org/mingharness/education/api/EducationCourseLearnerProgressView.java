package org.mingharness.education.api;

import java.time.Instant;

/** 课程教师视图中的单个学习者进度，覆盖待处理状态而不是只返回完成率。 */
public record EducationCourseLearnerProgressView(
        String learnerUserId,
        long assignmentTotal,
        long assigned,
        long accepted,
        long awaitingEvidence,
        long retryRequired,
        long overdue,
        long completed,
        long cancelled,
        long reviewPending,
        long reviewVerified,
        long revisionRequired,
        long openInterventionCount,
        double averageMasteryProgress,
        double averageMasteryGain,
        Instant lastActivityAt
) {
}
