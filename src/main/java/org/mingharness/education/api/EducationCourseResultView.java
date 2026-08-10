package org.mingharness.education.api;

import org.mingharness.education.EducationCourseLearnerResult;
import org.mingharness.education.EducationCourseResult;

import java.time.Instant;
import java.util.List;

public record EducationCourseResultView(
        String id,
        String courseId,
        String courseTitle,
        String courseCode,
        long activeLearnerTotal,
        long learnersWithAssignments,
        long effectiveAssignmentTotal,
        long assignmentCompleted,
        long assignmentVerified,
        long submissionCovered,
        double averageMasteryProgress,
        double averageMasteryGain,
        Instant completedAt,
        String completedByUserId,
        List<EducationCourseLearnerResultView> learners
) {
    public static EducationCourseResultView from(EducationCourseResult result,
                                                  EducationCourseView course,
                                                  List<EducationCourseLearnerResult> learners) {
        return new EducationCourseResultView(result.getId(), result.getCourseId(), course.title(),
                course.code(), result.getActiveLearnerTotal(), result.getLearnersWithAssignments(),
                result.getEffectiveAssignmentTotal(), result.getAssignmentCompleted(),
                result.getAssignmentVerified(), result.getSubmissionCovered(),
                result.getAverageMasteryProgress(), result.getAverageMasteryGain(),
                result.getCompletedAt(), result.getCompletedByUserId(),
                learners.stream().map(EducationCourseLearnerResultView::from).toList());
    }
}
