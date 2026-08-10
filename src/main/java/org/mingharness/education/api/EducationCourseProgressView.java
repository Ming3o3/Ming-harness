package org.mingharness.education.api;

import java.util.List;

/** 教师课程级运营结果：名单、作业状态、确认状态和学习者进度在同一投影中可见。 */
public record EducationCourseProgressView(
        EducationCourseView course,
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
        double assignmentCompletionRate,
        double teacherVerificationRate,
        boolean readyToComplete,
        long completionBlockerCount,
        List<EducationCourseLearnerProgressView> learners,
        boolean truncated
) {
}
