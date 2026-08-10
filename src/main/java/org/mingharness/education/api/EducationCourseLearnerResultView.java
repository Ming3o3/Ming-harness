package org.mingharness.education.api;

import org.mingharness.education.EducationCourseLearnerResult;

import java.time.Instant;

public record EducationCourseLearnerResultView(
        String learnerUserId,
        long effectiveAssignmentTotal,
        long assignmentCompleted,
        long assignmentVerified,
        long submissionCovered,
        double averageMasteryProgress,
        double averageMasteryGain,
        Instant lastActivityAt
) {
    public static EducationCourseLearnerResultView from(EducationCourseLearnerResult result) {
        return new EducationCourseLearnerResultView(result.getLearnerUserId(),
                result.getEffectiveAssignmentTotal(), result.getAssignmentCompleted(),
                result.getAssignmentVerified(), result.getSubmissionCovered(),
                result.getAverageMasteryProgress(), result.getAverageMasteryGain(),
                result.getLastActivityAt());
    }
}
