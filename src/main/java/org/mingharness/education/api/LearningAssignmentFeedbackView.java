package org.mingharness.education.api;

import org.mingharness.education.LearningAssignmentFeedback;

import java.time.Instant;

/** 作业反馈的安全投影，教师和学习者都可查看但不能修改原始反馈。 */
public record LearningAssignmentFeedbackView(
        String id,
        String learningAssignmentId,
        String teacherUserId,
        String learnerUserId,
        String action,
        String status,
        String message,
        Instant suggestedDueAt,
        Instant createdAt,
        Instant acknowledgedAt,
        Instant updatedAt
) {
    public static LearningAssignmentFeedbackView from(LearningAssignmentFeedback feedback) {
        return new LearningAssignmentFeedbackView(
                feedback.getId(), feedback.getLearningAssignmentId(), feedback.getTeacherUserId(),
                feedback.getLearnerUserId(), feedback.getAction().name(), feedback.getStatus().name(),
                feedback.getMessage(), feedback.getSuggestedDueAt(), feedback.getCreatedAt(),
                feedback.getAcknowledgedAt(), feedback.getUpdatedAt());
    }
}
