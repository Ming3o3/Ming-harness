package org.mingharness.education.api;

import org.mingharness.education.LearningAssignment;

import java.time.Instant;

/** 课程作业安全投影。 */
public record LearningAssignmentView(
        String id,
        String teacherUserId,
        String learnerUserId,
        String title,
        String instructions,
        String subject,
        String gradeLevel,
        String curriculumVersion,
        String conceptKey,
        double targetMastery,
        Instant dueAt,
        String status,
        String learnerProfileId,
        String learningGoalId,
        Instant createdAt,
        Instant updatedAt,
        Instant acceptedAt,
        Instant completedAt,
        String reviewStatus,
        Instant teacherReviewedAt,
        String teacherReviewerUserId,
        String teacherReviewNote
) {
    public static LearningAssignmentView from(LearningAssignment assignment) {
        return new LearningAssignmentView(assignment.getId(), assignment.getTeacherUserId(),
                assignment.getLearnerUserId(), assignment.getTitle(), assignment.getInstructions(),
                assignment.getSubject(), assignment.getGradeLevel(), assignment.getCurriculumVersion(),
                assignment.getConceptKey(), assignment.getTargetMastery(), assignment.getDueAt(),
                assignment.getStatus().name(), assignment.getLearnerProfileId(),
                assignment.getLearningGoalId(), assignment.getCreatedAt(), assignment.getUpdatedAt(),
                assignment.getAcceptedAt(), assignment.getCompletedAt(),
                assignment.getReviewStatus().name(), assignment.getTeacherReviewedAt(),
                assignment.getTeacherReviewerUserId(), assignment.getTeacherReviewNote());
    }
}
